/*
 * 2D Basketball - browser version
 *
 * A port of the Java game in /src to HTML5 canvas so it can be played from a link.
 * Same court, sprites and mechanics (shot meter with a green window, CPU that roams
 * and defends, steals, blocks, rebounds, 2s and 3s), with the bugs from the applet fixed:
 *
 *  - Every animation frame is anchored at the character's feet using measured sprite
 *    data (sprites.js), so there is no popping/teleporting when animations change and
 *    the CPU no longer disappears on frames that were cut outside its sprite sheet.
 *  - Game logic and drawing are separated; drawing never changes game state.
 *  - Fixed 60 updates per second regardless of monitor refresh rate.
 *  - Players have a ground position plus a jump height, so jumping never moves them
 *    across the court, and they are kept inside the floor with smooth sliding.
 *  - The ball leaves the shooter's actual hand position and flies on a real arc
 *    with rim, backboard and floor bounces.
 */
'use strict';

// ------------------------------------------------------------------ constants
const W = 1710;
const H = 1080;
const STEP = 1000 / 60;

const PLAYER_GRAVITY = 0.8;
const BALL_GRAVITY = 0.4;
const BALL_R = 22;
const RIM_R = 30;

const WALK_SPEED = 8;
const RUN_SPEED = 14;
const DRIBBLE_SPEED = 10;

const WIN_SCORE = 21;

// Floor area players can stand on (feet position). Trapezoid that follows the court.
const FLOOR = {
  top: 650,
  bottom: 1000,
  leftAt(y) { return lerp(240, 75, (y - this.top) / (this.bottom - this.top)); },
  rightAt(y) { return lerp(1470, 1635, (y - this.top) / (this.bottom - this.top)); },
};

// Hoops in screen coordinates. floorY = depth of the floor under the rim.
// Player attacks the RIGHT hoop, CPU attacks the LEFT hoop.
const HOOPS = {
  right: { x: 1492, rimY: 472, floorY: 800, boardX: 1550, dir: 1, threeX: 1098 },
  left:  { x: 221,  rimY: 472, floorY: 800, boardX: 164,  dir: -1, threeX: 612 },
};
for (const h of Object.values(HOOPS)) h.rimZ = h.floorY - h.rimY;

const SHOT_METER = { max: 100, greenStart: 80, greenEnd: 92, fillPerFrame: 2 };

// ------------------------------------------------------------------ helpers
function lerp(a, b, t) { return a + (b - a) * t; }
function clamp(v, lo, hi) { return v < lo ? lo : v > hi ? hi : v; }
function rand(lo, hi) { return lo + Math.random() * (hi - lo); }
function sign(v) { return v < 0 ? -1 : 1; }

function clampToFloor(o, margin = 0) {
  o.y = clamp(o.y, FLOOR.top + margin, FLOOR.bottom - margin);
  o.x = clamp(o.x, FLOOR.leftAt(o.y) + margin, FLOOR.rightAt(o.y) - margin);
}

// ------------------------------------------------------------------ canvas + input
const canvas = document.getElementById('game');
const ctx = canvas.getContext('2d');

const keys = Object.create(null);
const pressedThisFrame = Object.create(null);
const GAME_KEYS = new Set([
  'KeyW', 'KeyA', 'KeyS', 'KeyD', 'KeyC', 'KeyO', 'KeyM', 'KeyP', 'Space',
  'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'ShiftLeft', 'ShiftRight',
  'Enter', 'Escape', 'KeyR', 'KeyQ',
]);

// Don't swallow browser shortcuts like Cmd/Ctrl+R.
function isShortcut(e) { return e.metaKey || e.ctrlKey || e.altKey; }

window.addEventListener('keydown', (e) => {
  if (isShortcut(e)) return;
  if (GAME_KEYS.has(e.code)) e.preventDefault();
  if (!keys[e.code] && !e.repeat) pressedThisFrame[e.code] = true;
  keys[e.code] = true;
});
window.addEventListener('keyup', (e) => {
  if (GAME_KEYS.has(e.code) && !isShortcut(e)) e.preventDefault();
  keys[e.code] = false;
});
// Releasing focus would otherwise leave keys "stuck down".
window.addEventListener('blur', () => {
  for (const k in keys) keys[k] = false;
  if (game.state === 'playing') game.pause();
});
document.addEventListener('visibilitychange', () => {
  if (document.hidden && game.state === 'playing') game.pause();
});

function wasPressed(...codes) { return codes.some((c) => pressedThisFrame[c]); }
function isDown(...codes) { return codes.some((c) => keys[c]); }

function toGameCoords(e) {
  const r = canvas.getBoundingClientRect();
  return { x: (e.clientX - r.left) * (W / r.width), y: (e.clientY - r.top) * (H / r.height) };
}
// pointer cursor over anything clickable
function updateCursor(p) {
  canvas.style.cursor = game.buttonAt(p.x, p.y) ? 'pointer' : 'default';
}
canvas.addEventListener('pointerdown', (e) => {
  canvas.focus();
  const p = toGameCoords(e);
  game.click(p.x, p.y);
  updateCursor(p);      // the screen just changed under the mouse
});
canvas.addEventListener('pointermove', (e) => updateCursor(toGameCoords(e)));

// ------------------------------------------------------------------ assets
const images = {};

function loadImage(src) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error('Could not load ' + src));
    img.src = src;
  });
}

async function loadAssets(onProgress) {
  const jobs = [
    ['court', 'assets/court.webp'],
    ['ball', 'assets/ball.webp'],
  ];
  for (const [key, sheet] of Object.entries(SPRITES)) {
    jobs.push([key, 'assets/' + sheet.file.replace('.png', '.webp')]);
  }
  let done = 0;
  await Promise.all(jobs.map(async ([key, src]) => {
    images[key] = await loadImage(src);
    done++;
    onProgress(done / jobs.length);
  }));
  // wait briefly for the web fonts, but never block the game on them
  const fonts = Promise.all([
    document.fonts.load('48px "Bungee"'),
    document.fonts.load('700 24px "Rubik"'),
  ]).catch(() => {});
  await Promise.race([fonts, new Promise((r) => setTimeout(r, 1500))]);
}

// ------------------------------------------------------------------ sprite drawing
/**
 * Draws one measured frame so that its feet anchor lands on (feetX, feetY).
 * flip = mirror horizontally.
 */
function drawFrame(sheetKey, row, col, flip, feetX, feetY) {
  const s = SPRITES[sheetKey];
  const f = s.frames[row][col];
  const img = images[sheetKey];
  const k = s.scale;
  const ws = s.webScale;
  const dw = f.w * k;
  const dh = f.h * k;
  const dy = feetY - (f.ay - f.y) * k;
  ctx.save();
  if (flip) {
    const dx = feetX - (f.x + f.w - f.ax) * k;
    ctx.translate(dx + dw, dy);
    ctx.scale(-1, 1);
    ctx.drawImage(img, f.x * ws, f.y * ws, f.w * ws, f.h * ws, 0, 0, dw, dh);
  } else {
    const dx = feetX - (f.ax - f.x) * k;
    ctx.drawImage(img, f.x * ws, f.y * ws, f.w * ws, f.h * ws, dx, dy, dw, dh);
  }
  ctx.restore();
}

function frameHandPoint(sheetKey, row, col, flip, feetX, feetY) {
  const s = SPRITES[sheetKey];
  const f = s.frames[row][col];
  const dx = (f.hx - f.ax) * s.scale;
  return { x: feetX + (flip ? -dx : dx), y: feetY + (f.hy - f.ay) * s.scale };
}

const ROW_BY_FACING = { R: 0, L: 1, F: 2, A: 3 };

// ------------------------------------------------------------------ actor (player + CPU)
class Actor {
  constructor(prefix, name) {
    this.prefix = prefix;   // 'p1' or 'p2' (which sprite sheets)
    this.name = name;
    this.reset(0, 0, 'R');
  }

  reset(x, y, facing) {
    this.x = x;
    this.y = y;
    this.z = 0;             // jump height
    this.vz = 0;
    this.facing = facing;   // R, L, F (toward camera), A (away)
    this.side = facing === 'L' ? 'L' : 'R';   // last left/right facing
    this.hasBall = false;
    this.action = null;     // null | 'shoot' | 'jump' | 'reach'
    this.actionT = 0;
    this.moving = false;
    this.running = false;
    this.animKey = '';
    this.animFrame = 0;
    this.animT = 0;
    this.stealCooldown = 0;
    this.pose = { sheet: this.prefix + '_idle', row: 0, col: 0, flip: true };
  }

  get airborne() { return this.z > 0 || this.vz > 0; }
  get canAct() { return this.action === null && !this.airborne; }

  /** dx, dy are -1/0/1 directions. */
  move(dx, dy, speed, run) {
    if (this.action === 'shoot' || this.action === 'reach') return;
    if (!dx && !dy) return;
    const len = Math.hypot(dx, dy);
    this.x += (dx / len) * speed;
    this.y += (dy / len) * speed;
    if (dx) {
      this.facing = dx < 0 ? 'L' : 'R';
      this.side = this.facing;
    } else {
      this.facing = dy < 0 ? 'A' : 'F';
    }
    this.moving = true;
    this.running = run;
  }

  faceToward(x) {
    this.facing = x < this.x ? 'L' : 'R';
    this.side = this.facing;
  }

  startShot(hoop) {
    if (!this.canAct || !this.hasBall) return false;
    // always turn to face the hoop before shooting (even if facing the crowd / camera)
    this.faceToward(hoop.x);
    this.action = 'shoot';
    this.actionT = 0;
    return true;
  }

  startJump() {
    if (!this.canAct || this.hasBall) return false;
    this.action = 'jump';
    this.actionT = 0;
    return true;
  }

  startReach(towardX) {
    if (!this.canAct || this.hasBall || this.stealCooldown > 0) return false;
    this.faceToward(towardX);
    this.action = 'reach';
    this.actionT = 0;
    this.stealCooldown = 180;       // 3 seconds
    return true;
  }

  cancelAction() {
    this.action = null;
    this.actionT = 0;
  }

  /** Physics + action timers. Returns 'release' on the frame a shot leaves the hands. */
  update() {
    let event = null;
    if (this.stealCooldown > 0) this.stealCooldown--;

    if (this.action === 'shoot') {
      this.actionT++;
      if (this.actionT === 32) this.vz = 8;                 // small hop into the jumper
      if (this.actionT >= 50) {
        this.action = null;
        event = 'release';
      }
    } else if (this.action === 'jump') {
      this.actionT++;
      if (this.actionT === 6) this.vz = 14;                 // take off after a short crouch
      if (this.actionT > 6 && !this.airborne) this.action = null;
    } else if (this.action === 'reach') {
      this.actionT++;
      if (this.actionT >= 24) this.action = null;
    }

    if (this.z > 0 || this.vz !== 0) {
      this.z += this.vz;
      this.vz -= PLAYER_GRAVITY;
      if (this.z <= 0) {
        this.z = 0;
        this.vz = 0;
      }
    }
    return event;
  }

  /** Picks the sprite frame for this tick. Called once per update, never while drawing. */
  updatePose() {
    const p = this.prefix;
    let sheet, row, frames, speed, flip = false, col = null;

    if (this.action === 'shoot') {
      sheet = p + '_shoot';
      row = ROW_BY_FACING[this.facing];
      col = Math.min(4, Math.floor(this.actionT / 10));
    } else if (this.action === 'jump') {
      sheet = p + '_jump';
      row = 0;
      if (this.actionT < 3) col = 0;
      else if (this.actionT < 6) col = 1;
      else if (this.vz > 7) col = 2;
      else if (this.vz > 0) col = 3;
      else if (this.vz > -6) col = 4;
      else col = 5;
      flip = SPRITES[sheet].faces[0] !== this.side;
    } else if (this.action === 'reach') {
      sheet = p + '_reach';
      row = 0;
      col = Math.min(2, Math.floor(this.actionT / 8));
      flip = SPRITES[sheet].faces[0] !== this.side;
    } else if (this.hasBall) {
      sheet = p + '_dribble';
      row = ROW_BY_FACING[this.facing];
      frames = 5;
      speed = this.moving ? 6 : 9;
    } else if (this.moving && (this.facing === 'L' || this.facing === 'R')) {
      sheet = p + '_walk';
      row = this.running ? 2 : 0;
      frames = 4;
      speed = this.running ? 5 : 8;
      flip = this.facing === 'L';
    } else if (this.moving) {
      sheet = p + '_updown';
      row = this.facing === 'A' ? 0 : 1;
      frames = 8;
      speed = 5;
    } else {
      sheet = p + '_idle';
      row = 0;
      if (this.facing === 'F') col = 2;
      else if (this.facing === 'A') col = 3;
      else { col = 0; flip = this.facing === 'R'; }
    }

    const key = sheet + ':' + row;
    if (key !== this.animKey) {
      this.animKey = key;
      this.animFrame = 0;
      this.animT = 0;
    }
    if (col === null) {
      this.animT++;
      if (this.animT >= speed) {
        this.animT = 0;
        this.animFrame = (this.animFrame + 1) % frames;
      }
      col = this.animFrame;
    }
    this.pose = { sheet, row, col, flip };
    this.moving = false;
    this.running = false;
  }

  handPoint() {
    const { sheet, row, col, flip } = this.pose;
    if (!sheet.endsWith('_shoot')) {
      return { x: this.x + (this.side === 'L' ? -40 : 40), y: this.y - this.z - 120 };
    }
    return frameHandPoint(sheet, row, col, flip, this.x, this.y - this.z);
  }

  drawShadow() {
    const s = Math.max(0.45, 1 - this.z / 300);
    ctx.fillStyle = `rgba(0,0,0,${0.28 * s})`;
    ctx.beginPath();
    ctx.ellipse(this.x, this.y - 2, 52 * s, 14 * s, 0, 0, Math.PI * 2);
    ctx.fill();
  }

  draw() {
    const { sheet, row, col, flip } = this.pose;
    drawFrame(sheet, row, col, flip, this.x, this.y - this.z);
    // the shooting / jumping sprites have no ball in them, so draw it in the hands
    if ((this.action === 'shoot' || this.action === 'jump') && this.hasBall) {
      const h = this.handPoint();
      drawBallAt(h.x, h.y);
    }
  }
}

// ------------------------------------------------------------------ ball
const ball = {
  x: 855, y: 800, z: 0,
  vx: 0, vy: 0, vz: 0,
  state: 'loose',     // 'held' | 'loose' | 'shot' | 'net'
  holder: null,
  shot: null,
  spin: 0,
};

function drawBallAt(x, y, rot = 0) {
  ctx.save();
  ctx.translate(x, y);
  if (rot) ctx.rotate(rot);
  ctx.drawImage(images.ball, -BALL_R, -BALL_R, BALL_R * 2, BALL_R * 2);
  ctx.restore();
}

// ------------------------------------------------------------------ shot meter
const meter = {
  charging: false,
  released: false,
  value: 0,
  releaseValue: -1,

  start() {
    this.charging = true;
    this.released = false;
    this.value = 0;
    this.releaseValue = -1;
  },
  reset() {
    this.charging = false;
    this.released = false;
    this.value = 0;
    this.releaseValue = -1;
  },
  update(holdingShoot) {
    if (!this.charging) return;
    if (!this.released) {
      if (!holdingShoot) {
        this.released = true;
        this.releaseValue = this.value;
      } else {
        this.value = Math.min(SHOT_METER.max, this.value + SHOT_METER.fillPerFrame);
        // held too long: forced (bad) release
        if (this.value >= SHOT_METER.max) {
          this.released = true;
          this.releaseValue = SHOT_METER.max;
        }
      }
    }
  },
  get green() {
    return this.releaseValue >= SHOT_METER.greenStart && this.releaseValue <= SHOT_METER.greenEnd;
  },
  draw(x, y) {
    if (!this.charging) return;
    const w = 110, h = 14, mx = Math.round(x - w / 2), my = Math.round(y);
    const gs = mx + (SHOT_METER.greenStart / SHOT_METER.max) * w;
    const ge = mx + (SHOT_METER.greenEnd / SHOT_METER.max) * w;
    ctx.fillStyle = 'rgba(0,0,0,0.55)';
    ctx.fillRect(mx - 4, my - 7, w + 8, h + 14);
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(mx, my, gs - mx, h);
    ctx.fillStyle = '#21d24a';
    ctx.fillRect(gs, my, ge - gs, h);
    ctx.fillStyle = '#ff8c00';
    ctx.fillRect(ge, my, mx + w - ge, h);
    const fx = mx + (this.value / SHOT_METER.max) * w;
    ctx.fillStyle = '#ffe600';
    ctx.fillRect(fx - 2, my - 4, 5, h + 8);
    if (this.released) {
      const rx = mx + (this.releaseValue / SHOT_METER.max) * w;
      ctx.fillStyle = '#00e5ff';
      ctx.fillRect(rx - 2, my - 4, 5, h + 8);
    }
    ctx.strokeStyle = '#000';
    ctx.lineWidth = 2;
    ctx.strokeRect(mx, my, w, h);
  },
};

// ------------------------------------------------------------------ game
const player = new Actor('p1', 'YOU');
const cpu = new Actor('p2', 'CPU');

const cpuBrain = {
  state: 'GET_BALL',
  roamX: 0, roamY: 0, roamSet: false,
  spotsTotal: 3, spotsVisited: 0,
  roamTimer: 0,
  contestAt: -1,      // player's shot tick at which the CPU jumps to contest

  newPossession() {
    this.state = 'ROAM';
    this.roamSet = false;
    this.spotsTotal = 2 + Math.floor(Math.random() * 3);   // 2-4 spots
    this.spotsVisited = 0;
    this.roamTimer = 480 + Math.random() * 240;           // 8-12 s fallback
  },
};

const game = {
  state: 'loading',         // loading | menu | playing | paused | gameover
  loadProgress: 0,
  loadError: null,
  score: { player: 0, cpu: 0 },
  toasts: [],
  reset: null,              // possession reset after a basket {t, possession}
  fade: 0,
  winner: null,             // 'player' | 'cpu' once someone reaches WIN_SCORE
  menuButton: { x: W / 2 - 150, y: 880, w: 300, h: 78 },
  pauseButton: { x: 28, y: 22, w: 214, h: 58 },          // top-left corner during a game
  pauseMenu: [                                           // buttons on the pause screen
    { id: 'resume', label: 'RESUME', x: W / 2 - 190, y: 480, w: 380, h: 78 },
    { id: 'restart', label: 'RESTART', x: W / 2 - 190, y: 580, w: 380, h: 78 },
    { id: 'menu', label: 'MAIN MENU', x: W / 2 - 190, y: 680, w: 380, h: 78 },
  ],
  hintTimer: 0,
  stealGrace: 0,            // frames the CPU gives you space after you inbound the ball

  newGame() {
    this.score.player = 0;
    this.score.cpu = 0;
    this.toasts = [];
    this.reset = null;
    this.fade = 0;
    meter.reset();
    // opening tip: ball at center court, first one to grab it gets possession
    player.reset(560, 900, 'R');
    cpu.reset(1150, 700, 'L');
    ball.state = 'loose';
    ball.holder = null;
    ball.shot = null;
    ball.x = 855; ball.y = 800; ball.z = 120;
    ball.vx = 0; ball.vy = 0; ball.vz = 0;
    cpuBrain.state = 'GET_BALL';
    cpuBrain.contestAt = -1;
    this.hintTimer = 360;
    this.stealGrace = 0;
    this.state = 'playing';
    this.toast('GRAB THE BALL!', 855, 560, '#ffd23f', 90, 46);
  },

  pause() { if (this.state === 'playing') this.state = 'paused'; },

  /** Which clickable thing (if any) is under (x, y) on the current screen. */
  buttonAt(x, y) {
    const hit = (b) => x >= b.x && x <= b.x + b.w && y >= b.y && y <= b.y + b.h;
    if ((this.state === 'menu' || this.state === 'gameover') && hit(this.menuButton)) return 'play';
    if (this.state === 'playing' && hit(this.pauseButton)) return 'pause';
    if (this.state === 'paused') {
      const b = this.pauseMenu.find(hit);
      if (b) return b.id;
    }
    return null;
  },

  click(x, y) {
    switch (this.buttonAt(x, y)) {
      case 'play': this.newGame(); break;
      case 'pause': this.pause(); break;
      case 'resume': this.state = 'playing'; break;
      case 'restart': this.newGame(); break;
      case 'menu': this.state = 'menu'; break;
    }
  },

  toast(text, x, y, color = '#fff', life = 70, size = 40) {
    this.toasts.push({ text, x, y, color, t: 0, life, size });
  },

  // ---------------------------------------------------------------- update
  update() {
    switch (this.state) {
      case 'menu':
        if (wasPressed('Enter', 'Space')) this.newGame();
        break;
      case 'paused':
        if (wasPressed('Escape', 'Enter')) this.state = 'playing';
        else if (wasPressed('KeyR')) this.newGame();
        else if (wasPressed('KeyQ')) this.state = 'menu';
        break;
      case 'gameover':
        if (wasPressed('Enter', 'Space')) this.newGame();
        else if (wasPressed('Escape', 'KeyQ')) this.state = 'menu';
        else this.updateWorld(false);
        break;
      case 'playing':
        if (wasPressed('Escape')) { this.state = 'paused'; break; }
        this.updateWorld(true);
        break;
    }
    for (const k in pressedThisFrame) delete pressedThisFrame[k];
  },

  updateWorld(allowInput) {
    const inputLocked = !allowInput || this.reset !== null;
    for (const a of [player, cpu]) { a.prevX = a.x; a.prevY = a.y; }
    if (!inputLocked) this.updatePlayerInput();
    else meter.update(false);
    if (this.reset === null && this.state === 'playing') this.updateCpu();

    for (const a of [player, cpu]) {
      if (a.update() === 'release') this.launchShot(a);
    }
    this.separateActors();
    this.updateBall();
    player.updatePose();
    cpu.updatePose();

    if (this.hintTimer > 0) this.hintTimer--;
    if (this.stealGrace > 0) this.stealGrace--;
    for (const t of this.toasts) t.t++;
    this.toasts = this.toasts.filter((t) => t.t < t.life);
    this.updatePossessionReset();
  },

  updatePlayerInput() {
    const left = isDown('KeyA', 'ArrowLeft');
    const right = isDown('KeyD', 'ArrowRight');
    const up = isDown('KeyW', 'ArrowUp');
    const down = isDown('KeyS', 'ArrowDown');
    const dx = (right ? 1 : 0) - (left ? 1 : 0);
    const dy = (down ? 1 : 0) - (up ? 1 : 0);
    const run = isDown('KeyC', 'ShiftLeft', 'ShiftRight');

    const speed = player.hasBall ? DRIBBLE_SPEED : run ? RUN_SPEED : WALK_SPEED;
    player.move(dx, dy, speed, run && !player.hasBall);

    // shoot: hold O to charge, release in the green
    if (wasPressed('KeyO') && player.hasBall && player.startShot(HOOPS.right)) {
      meter.start();
    }
    meter.update(isDown('KeyO'));

    // jump / block
    if (wasPressed('Space')) player.startJump();

    // reach / steal
    if (wasPressed('KeyM') && player.startReach(cpu.x)) {
      const close = Math.abs(player.x - cpu.x) < 140 && Math.abs(player.y - cpu.y) < 50;
      if (close && cpu.hasBall && cpu.action !== 'shoot') this.resolveSteal(player, cpu);
    }

    // pick up / drop
    if (wasPressed('KeyP')) {
      if (player.hasBall && player.action === null) {
        player.hasBall = false;
        ball.state = 'loose';
        ball.holder = null;
        ball.x = player.x + (player.side === 'L' ? -45 : 45);
        ball.y = player.y + 4;
        ball.z = 40; ball.vx = 0; ball.vy = 0; ball.vz = 0;
        cpuBrain.state = 'GET_BALL';
      } else if (this.canGrab(player)) {
        this.giveBall(player);
      }
    }
  },

  canGrab(a) {
    return ball.state === 'loose' && !a.hasBall && a.action !== 'shoot' &&
      Math.abs(ball.x - a.x) < 80 && Math.abs(ball.y - a.y) < 45 &&
      ball.z < 170 + a.z;
  },

  giveBall(a) {
    const other = a === player ? cpu : player;
    other.hasBall = false;
    a.hasBall = true;
    ball.state = 'held';
    ball.holder = a;
    ball.shot = null;
    ball.vx = ball.vy = ball.vz = 0;
    if (a === cpu) { cpuBrain.newPossession(); this.stealGrace = 0; }   // grace only covers your inbound
    if (a === player) this.hintTimer = 0;
  },

  resolveSteal(thief, victim) {
    const roll = Math.random();
    if (roll < 0.2) {
      this.giveBall(thief);
      this.toast('STOLEN!', thief.x, thief.y - 260, '#ffd23f');
    } else if (roll < 0.5) {
      victim.hasBall = false;
      ball.state = 'loose';
      ball.holder = null;
      ball.x = victim.x;
      ball.y = victim.y;
      ball.z = 60;
      const dir = thief.x < victim.x ? 1 : -1;
      ball.vx = dir * rand(4, 8);
      ball.vy = rand(-2.5, 2.5);
      ball.vz = rand(2, 4);
      this.toast('KNOCKED LOOSE!', victim.x, victim.y - 260, '#ffffff', 60, 32);
    }
  },

  // ---------------------------------------------------------------- CPU
  updateCpu() {
    const b = cpuBrain;
    // decide state from the situation every frame so it can never get stuck
    if (cpu.hasBall) {
      if (b.state !== 'ROAM' && b.state !== 'SHOOT' && b.state !== 'SHOOTING') b.state = 'ROAM';
    } else if (player.hasBall) b.state = 'DEFEND';
    else if (ball.state === 'shot' || ball.state === 'net') b.state = 'WAIT';
    else b.state = 'GET_BALL';

    const go = (tx, ty, speed, run = false, tol = speed) => {
      const dx = Math.abs(tx - cpu.x) > tol ? sign(tx - cpu.x) : 0;
      let dy = Math.abs(ty - cpu.y) > tol ? sign(ty - cpu.y) : 0;
      // walk around the player instead of into him
      const ahead = (player.x - cpu.x) * dx;
      if (dx !== 0 && ahead > 0 && ahead < 110 && Math.abs(player.y - cpu.y) < 34 &&
          Math.abs(tx - cpu.x) > ahead) {
        dy = cpu.y < player.y ? -1 : 1;
        if (cpu.y - 40 < FLOOR.top) dy = 1;
        if (cpu.y + 40 > FLOOR.bottom) dy = -1;
      }
      cpu.move(dx, dy, speed, run);
      return dx === 0 && dy === 0;
    };

    switch (b.state) {
      case 'GET_BALL': {
        go(ball.x, ball.y, 9, true, 6);
        if (this.canGrab(cpu) && Math.abs(ball.x - cpu.x) < 60) this.giveBall(cpu);
        break;
      }
      case 'WAIT': {
        // box out for the rebound near whichever hoop the ball is going to
        const h = ball.shot ? ball.shot.hoop : HOOPS.left;
        go(h.x - h.dir * 170, h.floorY + 60, 6);
        break;
      }
      case 'ROAM': {
        if (!b.roamSet) {
          b.roamX = rand(280, 1050);
          b.roamY = rand(690, 980);
          b.roamSet = true;
        }
        const reached = go(b.roamX, b.roamY, DRIBBLE_SPEED);
        if (reached) {
          b.spotsVisited++;
          b.roamSet = false;
          if (b.spotsVisited >= b.spotsTotal) b.state = 'SHOOT';
        }
        b.roamTimer--;
        if (b.roamTimer <= 0) b.state = 'SHOOT';
        break;
      }
      case 'SHOOT': {
        if (cpu.startShot(HOOPS.left)) b.state = 'SHOOTING';
        break;
      }
      case 'DEFEND': {
        // stay between the player and the right hoop, a step off him
        // (right after you inbound, it gives you extra room and doesn't reach)
        const cushion = this.stealGrace > 0 ? 300 : 120;
        go(player.x + cushion, player.y, 8, false, 10);
        cpu.faceToward(player.x);
        const close = Math.abs(player.x - cpu.x) < 130 && Math.abs(player.y - cpu.y) < 45;
        if (close && this.stealGrace === 0 && player.action !== 'shoot' && Math.random() < 0.02 &&
            cpu.startReach(player.x)) {
          this.resolveSteal(cpu, player);
        }
        break;
      }
    }

    // contest the player's jump shot
    if (player.action === 'shoot') {
      if (player.actionT === 1) {
        const near = Math.abs(player.x - cpu.x) < 230 && Math.abs(player.y - cpu.y) < 60 && cpu.x > player.x - 40;
        b.contestAt = near && Math.random() < 0.4 ? 37 + Math.floor(Math.random() * 4) : -1;
      }
      if (player.actionT === b.contestAt) { cpu.faceToward(player.x); cpu.startJump(); }
    }
  },

  // ---------------------------------------------------------------- shooting
  launchShot(a) {
    if (!a.hasBall) { if (a === player) meter.reset(); return; }
    const hoop = a === player ? HOOPS.right : HOOPS.left;
    const hand = a.handPoint();
    a.hasBall = false;

    ball.state = 'shot';
    ball.holder = null;
    ball.x = hand.x;
    ball.y = a.y;
    ball.z = a.y - hand.y;            // height above the floor

    const dx = hoop.x - ball.x;
    const T = clamp(30 + Math.abs(dx) / 18, 36, 90);    // frames in the air
    let vx = dx / T;
    const vy = (hoop.floorY - ball.y) / T;
    let vz = (hoop.rimZ - ball.z + 0.5 * BALL_GRAVITY * T * T) / T;

    const three = hoop.dir > 0 ? a.x < hoop.threeX : a.x > hoop.threeX;
    let pf = 1, jx = 0, jz = 0;

    if (a === player) {
      if (!meter.released) { meter.released = true; meter.releaseValue = meter.value; }
      if (meter.green) {
        this.toast('GREEN!', a.x, a.y - 300, '#21d24a', 50, 34);
      } else {
        const center = (SHOT_METER.greenStart + SHOT_METER.greenEnd) / 2;
        const off = (meter.releaseValue - center) / (SHOT_METER.max / 2);   // early < 0 < late
        // the green window is near the end of the bar, so late misses ramp up faster;
        // holding until the meter maxes out is a really bad shot
        const late = off > 0;
        pf = 1 + off * (late ? 0.6 : 0.22);
        jx = rand(-1, 1) * (0.35 + Math.abs(off) * (late ? 2.2 : 0.8));
        jz = rand(-0.3, 0.3);
        if (meter.releaseValue >= SHOT_METER.max) {          // never let go: forced brick
          pf = rand(0.72, 0.82);                              // comes up short
          jx = rand(-0.5, 0.5);
        }
      }
      meter.reset();
    } else {
      const makeChance = three ? 0.38 : 0.5;
      if (Math.random() >= makeChance) {
        jx = sign(Math.random() - 0.5) * rand(0.45, 1.3);
        jz = rand(-0.4, 0.4);
      }
    }

    ball.vx = vx * pf + jx;
    ball.vy = vy;
    ball.vz = vz * pf + jz;
    ball.shot = { shooter: a, hoop, three, scored: false, t: 0, rimHits: 0, blockChecked: new Set() };
  },

  updateBall() {
    const b = ball;
    if (b.state === 'held') {
      b.x = b.holder.x;
      b.y = b.holder.y;
      b.z = 0;
      return;
    }

    const prevZ = b.z;
    b.x += b.vx;
    b.y += b.vy;
    b.z += b.vz;
    b.vz -= BALL_GRAVITY;
    b.spin += b.vx * 0.03;

    if (b.state === 'shot') this.updateShotFlight(prevZ);

    if (b.state === 'net') {
      // ball drops through the net, slowing down
      b.vx *= 0.85;
      b.vy *= 0.85;
      if (b.z <= 0) b.state = 'loose';
    }

    // floor bounce
    if (b.z <= 0) {
      b.z = 0;
      if (b.state === 'shot') b.state = 'loose';      // missed: now it's a rebound
      if (b.vz < -1.6) {
        b.vz = -b.vz * 0.55;
        b.vx *= 0.85;
        b.vy *= 0.85;
      } else {
        b.vz = 0;
      }
    }
    if (b.state === 'loose') {
      if (b.z === 0) { b.vx *= 0.93; b.vy *= 0.93; }
      if (Math.abs(b.vx) < 0.05) b.vx = 0;
      if (Math.abs(b.vy) < 0.05) b.vy = 0;
      // keep loose balls on the floor where players can reach them
      const before = { x: b.x, y: b.y };
      clampToFloor(b, 10);
      if (before.x !== b.x) b.vx = -b.vx * 0.5;
      if (before.y !== b.y) b.vy = -b.vy * 0.5;
    }
  },

  updateShotFlight(prevZ) {
    const b = ball;
    const s = b.shot;
    const h = s.hoop;
    s.t++;
    const nearDepth = Math.abs(b.y - h.floorY) < 70;

    // blocks: only early in the flight, by an airborne defender
    if (s.t < 28 && s.rimHits === 0) {
      for (const d of [player, cpu]) {
        if (d === s.shooter || d.z < 15 || s.blockChecked.has(d)) continue;
        const handsX = d.x + (d.side === 'L' ? -15 : 15);
        const top = d.y - d.z - 250;
        const bottom = d.y - d.z - 105;
        const sy = b.y - b.z;
        if (Math.abs(b.x - handsX) < 60 && sy > top && sy < bottom && Math.abs(b.y - d.y) < 70) {
          s.blockChecked.add(d);
          if (d === cpu && Math.random() > 0.7) continue;      // CPU doesn't block everything
          b.state = 'loose';
          b.vx = -sign(b.vx) * rand(3, 6);
          b.vy = rand(-2, 2);
          b.vz = rand(0, 2.5);
          this.toast('BLOCKED!', d.x, d.y - d.z - 290, '#ff4d4d', 70, 44);
          return;
        }
      }
    }

    // rim: check when the ball comes down through the rim's height
    if (!s.scored && prevZ >= h.rimZ && b.z < h.rimZ && nearDepth) {
      const d = b.x - h.x;
      if (Math.abs(d) <= RIM_R - 6) {
        this.scoreBasket(s);
      } else if (Math.abs(d) <= RIM_R + BALL_R && s.rimHits < 4) {
        s.rimHits++;
        b.z = h.rimZ;
        b.vz = Math.abs(b.vz) * 0.45 + 1.4;
        b.vx = b.vx * 0.35 + sign(d) * rand(1.2, 2.2);
      }
    }

    // backboard (and the padding / stanchion behind it): the ball never goes behind the hoop
    const pastBoard = h.dir > 0 ? b.x + BALL_R > h.boardX : b.x - BALL_R < h.boardX;
    if (pastBoard && b.vx * h.dir > 0) {
      const hitBoard = nearDepth && b.z > h.rimZ - 20 && b.z < h.rimZ + 160;
      b.x = h.boardX - h.dir * BALL_R;
      b.vx = -b.vx * (hitBoard ? 0.45 : 0.3);
    }
  },

  scoreBasket(s) {
    s.scored = true;
    ball.state = 'net';
    ball.vx = (s.hoop.x - ball.x) * 0.2;
    ball.vy = 0;
    ball.vz = -1.5;
    const pts = s.three ? 3 : 2;
    const byPlayer = s.shooter === player;
    if (byPlayer) this.score.player += pts; else this.score.cpu += pts;
    this.toast('+' + pts, s.hoop.x, s.hoop.rimY - 70, byPlayer ? '#ffd23f' : '#ff7ad9', 80, 64);

    const winner = this.score.player >= WIN_SCORE ? 'player' : this.score.cpu >= WIN_SCORE ? 'cpu' : null;
    if (winner) {
      this.state = 'gameover';
      this.winner = winner;
      this.reset = null;
      player.cancelAction();
      cpu.cancelAction();
      meter.reset();
      return;
    }
    // other side takes the ball out after a brief celebration
    this.reset = { t: 0, possession: byPlayer ? 'cpu' : 'player' };
  },

  updatePossessionReset() {
    const r = this.reset;
    if (!r) { this.fade = Math.max(0, this.fade - 0.08); return; }
    r.t++;
    if (r.t > 55) this.fade = Math.min(1, this.fade + 0.08);
    if (r.t === 70) {
      player.cancelAction(); cpu.cancelAction();
      player.z = player.vz = cpu.z = cpu.vz = 0;
      meter.reset();
      if (r.possession === 'player') {
        player.reset(420, 840, 'R');
        cpu.reset(820, 840, 'L');       // backed off so you aren't pressured right away
        this.giveBall(player);
        this.stealGrace = 120;          // 2 seconds
      } else {
        cpu.reset(1290, 840, 'L');
        player.reset(1010, 840, 'R');
        this.giveBall(cpu);
      }
      player.updatePose();
      cpu.updatePose();
      this.reset = null;
    }
  },

  // Keeps the two players from walking through each other without snapping them back.
  // Whoever walked into the other one is the one that gets stopped, so the CPU can't
  // shove you around and you can't bulldoze through its defense.
  separateActors() {
    const movedP = Math.hypot(player.x - player.prevX, player.y - player.prevY) > 0.1;
    const movedC = Math.hypot(cpu.x - cpu.prevX, cpu.y - cpu.prevY) > 0.1;
    let wp = 0.5;
    if (movedP && !movedC) wp = 1;
    else if (movedC && !movedP) wp = 0;
    for (let pass = 0; pass < 2; pass++) {
      const dx = cpu.x - player.x;
      const dy = cpu.y - player.y;
      const rx = 70, ry = 26;
      const px = rx - Math.abs(dx);
      const py = ry - Math.abs(dy);
      if (px > 0 && py > 0) {
        if (px / rx < py / ry) {
          player.x -= sign(dx) * px * wp;
          cpu.x += sign(dx) * px * (1 - wp);
        } else {
          player.y -= sign(dy) * py * wp;
          cpu.y += sign(dy) * py * (1 - wp);
        }
      }
      clampToFloor(player);
      clampToFloor(cpu);
      wp = 0.5;   // if a wall stopped the push, share what's left
    }
  },

  // ---------------------------------------------------------------- render
  render() {
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    if (this.state === 'loading') { this.renderLoading(); return; }

    ctx.drawImage(images.court, 0, 0, W, H);
    this.renderScoreboard();

    if (this.state === 'menu') { this.renderMenu(); return; }

    // shadows
    player.drawShadow();
    cpu.drawShadow();
    if (ball.state !== 'held') {
      const s = Math.max(0.35, 1 - ball.z / 400);
      ctx.fillStyle = `rgba(0,0,0,${0.3 * s})`;
      ctx.beginPath();
      ctx.ellipse(ball.x, ball.y, 20 * s, 7 * s, 0, 0, Math.PI * 2);
      ctx.fill();
    }

    // depth-sorted players (and a ball lying on the floor)
    const drawList = [player, cpu].map((a) => ({ y: a.y, draw: () => a.draw() }));
    const ballOnFloor = ball.state === 'loose' && ball.z < 60;
    if (ballOnFloor) drawList.push({ y: ball.y + 1, draw: () => drawBallAt(ball.x, ball.y - ball.z - BALL_R, ball.spin) });
    drawList.sort((a, b) => a.y - b.y);
    for (const d of drawList) d.draw();

    // ball in the air is always in front
    if (ball.state === 'shot' || ball.state === 'net' || (ball.state === 'loose' && !ballOnFloor)) {
      drawBallAt(ball.x, ball.y - ball.z - BALL_R, ball.spin);
    }

    meter.draw(player.x, player.y - player.z - 275);
    this.renderToasts();
    this.renderHints();

    if (this.fade > 0) {
      ctx.fillStyle = `rgba(5,8,18,${this.fade})`;
      ctx.fillRect(0, 0, W, H);
    }
    if (this.state === 'playing') this.renderPauseButton();
    if (this.state === 'paused') this.renderPause();
    if (this.state === 'gameover') this.renderGameOver();
  },

  renderPauseButton() {
    const b = this.pauseButton;
    ctx.save();
    ctx.fillStyle = 'rgba(8,12,28,0.78)';
    roundRect(b.x, b.y, b.w, b.h, b.h / 2); ctx.fill();
    ctx.strokeStyle = 'rgba(255,255,255,0.25)';
    ctx.lineWidth = 2;
    roundRect(b.x, b.y, b.w, b.h, b.h / 2); ctx.stroke();
    // pause icon (two bars)
    ctx.fillStyle = '#ffffff';
    const iy = b.y + b.h / 2 - 12;
    ctx.fillRect(b.x + 24, iy, 7, 24);
    ctx.fillRect(b.x + 37, iy, 7, 24);
    ctx.font = '800 24px "Rubik", sans-serif';
    ctx.textAlign = 'left';
    ctx.textBaseline = 'middle';
    ctx.fillText('PAUSE', b.x + 58, b.y + b.h / 2 + 1);
    ctx.font = '700 14px "Rubik", sans-serif';
    ctx.fillStyle = '#8f9ab5';
    ctx.textAlign = 'right';
    ctx.fillText('ESC', b.x + b.w - 24, b.y + b.h / 2 + 1);
    ctx.restore();
  },

  renderScoreboard() {
    // the scores go on the jumbotron that is part of the court art
    ctx.save();
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.font = '40px "Bungee", "Rubik", sans-serif';
    ctx.fillStyle = '#ffe14d';
    ctx.shadowColor = 'rgba(255,225,77,0.8)';
    ctx.shadowBlur = 14;
    ctx.fillText(String(this.score.player), 795, 214);
    ctx.fillStyle = '#ff6bf0';
    ctx.shadowColor = 'rgba(255,107,240,0.8)';
    ctx.fillText(String(this.score.cpu), 918, 214);
    ctx.shadowBlur = 0;
    // labels sit on the coloured bars above each score box
    ctx.font = '800 14px "Rubik", sans-serif';
    ctx.fillStyle = '#1b1530';
    ctx.fillText('YOU', 795, 169);
    ctx.fillText('CPU', 918, 169);
    ctx.font = '700 17px "Rubik", sans-serif';
    ctx.fillStyle = '#9fb3ff';
    ctx.fillText('FIRST TO ' + WIN_SCORE, 856, 302);
    ctx.restore();
  },

  renderToasts() {
    ctx.save();
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    for (const t of this.toasts) {
      const p = t.t / t.life;
      ctx.globalAlpha = p < 0.75 ? 1 : 1 - (p - 0.75) / 0.25;
      ctx.font = `${t.size}px "Bungee", "Rubik", sans-serif`;
      ctx.lineWidth = 8;
      ctx.strokeStyle = 'rgba(0,0,0,0.75)';
      const y = t.y - t.t * 0.8;
      ctx.strokeText(t.text, t.x, y);
      ctx.fillStyle = t.color;
      ctx.fillText(t.text, t.x, y);
    }
    ctx.restore();
  },

  renderHints() {
    let text = null;
    if (this.state !== 'playing' || this.reset) return;
    if (this.canGrab(player)) text = 'Press P to pick up the ball';
    else if (this.hintTimer > 0 && !player.hasBall) text = 'Run to the ball with WASD (hold C to sprint)';
    else if (player.hasBall && this.score.player === 0 && this.score.cpu === 0 && !meter.charging) {
      text = 'Hold O to shoot, release in the green. You attack the right hoop.';
    }
    if (!text) return;
    ctx.save();
    ctx.font = '700 26px "Rubik", sans-serif';
    ctx.textAlign = 'center';
    const w = ctx.measureText(text).width + 40;
    ctx.fillStyle = 'rgba(8,12,28,0.78)';
    roundRect(W / 2 - w / 2, 1022, w, 44, 22);
    ctx.fill();
    ctx.fillStyle = '#fff';
    ctx.textBaseline = 'middle';
    ctx.fillText(text, W / 2, 1045);
    ctx.restore();
  },

  renderLoading() {
    ctx.fillStyle = '#0b1020';
    ctx.fillRect(0, 0, W, H);
    ctx.fillStyle = '#e8ecf6';
    ctx.textAlign = 'center';
    ctx.font = '700 40px Rubik, system-ui, sans-serif';
    if (this.loadError) {
      ctx.fillText('Something went wrong loading the game.', W / 2, H / 2 - 20);
      ctx.font = '500 26px Rubik, system-ui, sans-serif';
      ctx.fillStyle = '#8f9ab5';
      ctx.fillText('Please refresh the page.', W / 2, H / 2 + 30);
      return;
    }
    ctx.fillText('Loading...', W / 2, H / 2 - 40);
    ctx.fillStyle = '#1d2640';
    roundRect(W / 2 - 300, H / 2, 600, 22, 11); ctx.fill();
    ctx.fillStyle = '#ff7a1a';
    roundRect(W / 2 - 300, H / 2, Math.max(22, 600 * this.loadProgress), 22, 11); ctx.fill();
  },

  renderMenu() {
    const g = ctx.createLinearGradient(0, 0, 0, H);
    g.addColorStop(0, 'rgba(6,9,22,0.72)');
    g.addColorStop(1, 'rgba(6,9,22,0.9)');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, W, H);

    ctx.save();
    ctx.textAlign = 'center';
    ctx.textBaseline = 'alphabetic';
    ctx.font = '104px "Bungee", "Rubik", sans-serif';
    ctx.lineWidth = 14;
    ctx.strokeStyle = '#120a04';
    ctx.strokeText('2D BASKETBALL', W / 2, 190);
    const tg = ctx.createLinearGradient(0, 100, 0, 200);
    tg.addColorStop(0, '#ffd23f');
    tg.addColorStop(1, '#ff7a1a');
    ctx.fillStyle = tg;
    ctx.fillText('2D BASKETBALL', W / 2, 190);
    ctx.font = '700 30px "Rubik", sans-serif';
    ctx.fillStyle = '#cfd8ff';
    ctx.fillText('1-on-1 against the CPU  ·  first to ' + WIN_SCORE + ' wins', W / 2, 248);

    // the two players on either side of the controls card
    const t = performance.now() / 1000;
    drawFrame('p1_idle', 0, 2, false, 300, 760 + Math.sin(t * 2) * 3);
    drawFrame('p2_idle', 0, 2, false, W - 300, 760 + Math.sin(t * 2 + 1) * 3);
    ctx.font = '700 26px "Rubik", sans-serif';
    ctx.fillStyle = '#ffb36b';
    ctx.fillText('YOU', 300, 805);
    ctx.fillStyle = '#ffe14d';
    ctx.fillText('CPU', W - 300, 805);

    // controls card
    const cx = W / 2 - 400, cy = 300, cw = 800, ch = 530;
    ctx.fillStyle = 'rgba(14,20,42,0.92)';
    roundRect(cx, cy, cw, ch, 26); ctx.fill();
    ctx.strokeStyle = 'rgba(255,255,255,0.12)';
    ctx.lineWidth = 2;
    roundRect(cx, cy, cw, ch, 26); ctx.stroke();

    ctx.font = '30px "Bungee", "Rubik", sans-serif';
    ctx.fillStyle = '#fff';
    ctx.fillText('CONTROLS', W / 2, cy + 62);

    const rows = [
      ['W A S D', 'Move  (arrow keys work too)'],
      ['C + move', 'Sprint (without the ball)'],
      ['Hold O', 'Shoot - release in the green'],
      ['Space', 'Jump to block a shot'],
      ['M', 'Reach for a steal'],
      ['P', 'Pick up / drop the ball'],
      ['Esc', 'Pause (or the button in the top-left)'],
    ];
    ctx.textBaseline = 'middle';
    rows.forEach(([k, d], i) => {
      const y = cy + 120 + i * 56;
      ctx.font = '800 24px "Rubik", sans-serif';
      const kw = Math.max(64, ctx.measureText(k).width + 30);
      const kx = cx + 250 - kw;
      ctx.fillStyle = '#26314f';
      roundRect(kx, y - 21, kw, 42, 10); ctx.fill();
      ctx.fillStyle = '#ffd23f';
      ctx.textAlign = 'center';
      ctx.fillText(k, kx + kw / 2, y + 1);
      ctx.textAlign = 'left';
      ctx.font = '500 25px "Rubik", sans-serif';
      ctx.fillStyle = '#e8ecf6';
      ctx.fillText(d, cx + 280, y + 1);
    });

    ctx.textAlign = 'center';
    ctx.font = '500 21px "Rubik", sans-serif';
    ctx.fillStyle = '#8f9ab5';
    ctx.fillText('You attack the right hoop, the CPU attacks the left.', W / 2, cy + ch - 26);

    this.renderButton('PLAY', t);
    ctx.font = '700 22px "Rubik", sans-serif';
    ctx.fillStyle = '#cfd8ff';
    ctx.fillText('or press ENTER', W / 2, 994);
    if (window.matchMedia && window.matchMedia('(pointer: coarse)').matches &&
        !window.matchMedia('(any-pointer: fine)').matches) {
      ctx.fillStyle = '#ffb36b';
      ctx.fillText('This game needs a keyboard - open it on a computer to play.', W / 2, 1040);
    }
    ctx.restore();
  },

  renderButton(label, t) {
    const b = this.menuButton;
    const pulse = 1 + Math.sin(t * 4) * 0.02;
    ctx.save();
    ctx.translate(b.x + b.w / 2, b.y + b.h / 2);
    ctx.scale(pulse, pulse);
    ctx.fillStyle = '#ff7a1a';
    ctx.shadowColor = 'rgba(255,122,26,0.6)';
    ctx.shadowBlur = 24;
    roundRect(-b.w / 2, -b.h / 2, b.w, b.h, 39); ctx.fill();
    ctx.shadowBlur = 0;
    ctx.fillStyle = '#1a0d02';
    ctx.font = '40px "Bungee", "Rubik", sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(label, 0, 3);
    ctx.restore();
  },

  renderPause() {
    ctx.fillStyle = 'rgba(6,9,22,0.7)';
    ctx.fillRect(0, 0, W, H);
    ctx.save();
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.font = '90px "Bungee", "Rubik", sans-serif';
    ctx.fillStyle = '#fff';
    ctx.fillText('PAUSED', W / 2, 380);
    for (const b of this.pauseMenu) {
      const primary = b.id === 'resume';
      ctx.fillStyle = primary ? '#ff7a1a' : 'rgba(255,255,255,0.1)';
      roundRect(b.x, b.y, b.w, b.h, b.h / 2); ctx.fill();
      if (!primary) {
        ctx.strokeStyle = 'rgba(255,255,255,0.3)';
        ctx.lineWidth = 2;
        roundRect(b.x, b.y, b.w, b.h, b.h / 2); ctx.stroke();
      }
      ctx.font = '34px "Bungee", "Rubik", sans-serif';
      ctx.fillStyle = primary ? '#1a0d02' : '#ffffff';
      ctx.fillText(b.label, b.x + b.w / 2, b.y + b.h / 2 + 2);
    }
    ctx.font = '700 22px "Rubik", sans-serif';
    ctx.fillStyle = '#cfd8ff';
    ctx.fillText('Keys:  Esc resume  ·  R restart  ·  Q main menu', W / 2, 820);
    ctx.restore();
  },

  renderGameOver() {
    ctx.fillStyle = 'rgba(6,9,22,0.72)';
    ctx.fillRect(0, 0, W, H);
    ctx.save();
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    const won = this.winner === 'player';
    ctx.font = '110px "Bungee", "Rubik", sans-serif';
    ctx.lineWidth = 14;
    ctx.strokeStyle = '#000';
    const text = won ? 'YOU WIN!' : 'CPU WINS';
    ctx.strokeText(text, W / 2, 400);
    ctx.fillStyle = won ? '#ffd23f' : '#ff6bf0';
    ctx.fillText(text, W / 2, 400);
    ctx.font = '56px "Bungee", "Rubik", sans-serif';
    ctx.fillStyle = '#fff';
    ctx.fillText(`${this.score.player} - ${this.score.cpu}`, W / 2, 530);
    this.renderButton('PLAY AGAIN', performance.now() / 1000);
    ctx.font = '700 22px "Rubik", sans-serif';
    ctx.fillStyle = '#cfd8ff';
    ctx.fillText('ENTER to play again  ·  Q for menu', W / 2, 994);
    ctx.restore();
  },
};

function roundRect(x, y, w, h, r) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}

// ------------------------------------------------------------------ main loop
let last = performance.now();
let acc = 0;

function frame(now) {
  acc += Math.min(250, now - last);   // don't spiral after a long tab switch
  last = now;
  while (acc >= STEP) {
    game.update();
    acc -= STEP;
  }
  game.render();
  requestAnimationFrame(frame);
}

loadAssets((p) => { game.loadProgress = p; })
  .then(() => { game.state = 'menu'; canvas.focus(); })
  .catch((err) => { console.error(err); game.loadError = err; });

requestAnimationFrame(frame);

// exposed for automated tests
window.__game = { game, player, cpu, ball, meter, cpuBrain, keys };
