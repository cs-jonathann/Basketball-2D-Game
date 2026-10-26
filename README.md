# 2D Basketball

A 1-on-1 basketball game against a CPU opponent. First to 21 wins.

**Play it in your browser:** https://basketball-2d-game-web.vercel.app/

The game was originally written in Java (Applets, Java 1.8) and is still fully playable as a
Java desktop app. Because browsers no longer run Java applets, there is also an HTML5 canvas
version of the same game in [`web/`](web/) that uses the same art and mechanics.

## Features
- Player vs CPU 1-on-1 on a full court, first to 21
- Shot meter with a timing-based green window (early shots fall short, late shots go long)
- Real ball flight: shots arc to the rim and can rattle in, bank off the backboard or bounce out
- CPU AI that hunts loose balls, roams with the ball before shooting, defends, contests shots,
  goes for steals and positions for rebounds
- Blocks, steals, knock-loose plays and rebounds
- 2 and 3 point scoring, scores shown on the arena jumbotron
- Main menu with controls, pause menu and game over screen

## Controls
| Key | Action |
|-----|--------|
| W A S D / arrow keys | Move |
| C (or Shift) + move | Sprint (without the ball) |
| O (hold) | Charge your shot, release in the green window |
| Space | Jump (block) |
| M | Reach / steal |
| P | Pick up / drop the ball |
| Esc | Pause |

You attack the **right** hoop; the CPU attacks the **left** hoop.

## Running the Java version
1. Import the project into Eclipse (or any IDE) as a Java project with `src/` as the source folder.
2. Run `Game.java` as a **Java Application** (it has a `main` method).

The game window scales to fit your screen. Images are loaded from `src/images/`.

From the command line:
```bash
javac -d bin src/*.java
cp -r src/images bin/
java -cp bin Game
```

## Running the web version locally
```bash
cd web
python3 -m http.server 8000
# open http://localhost:8000
```

## Deploying the web version
The `web/` folder is a static site with no build step. On Vercel: **Add New → Project**, import this
repository, set **Root Directory** to `web`, leave the framework preset as **Other**, and deploy.

## Project structure
```
src/                 Java version
  Game.java          game loop logic, screens, shooting, scoring
  GameBase.java      window, fixed 60 fps loop, keyboard input
  PlayerMovement.java player movement, actions and animation
  CPU.java           CPU opponent AI
  Ball.java          ball physics
  Court.java         floor boundaries and hoops
  ShotMeter.java     shot timing meter
  ScoreBoard.java    jumbotron score display
  SpriteSheet.java   sprite loading and feet-anchored drawing
  SpriteData.java    measured sprite frame data (generated)
  images/            sprites and court art
web/                 browser version (HTML5 canvas, no dependencies)
tools/
  build_sprites.py   measures every sprite frame and exports data + web images
```

### About `tools/build_sprites.py`
The sprite sheets were hand drawn, so the character isn't in the same spot in every frame, and
Player 1 and Player 2 sheets are different sizes. The script measures each frame's bounding box,
the feet position and the hand position on shooting frames, so every animation lines up and the
ball sits in the shooter's hands. If you edit or add sprites, re-run it:
```bash
pip install pillow numpy scipy
python3 tools/build_sprites.py
```

## Built With
- Java (Swing / Java2D), originally Applets with a GameBase framework provided by my professor
- HTML5 Canvas + JavaScript for the browser version
- Python (Pillow, NumPy, SciPy) for the sprite measuring tool
