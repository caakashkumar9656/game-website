# game-website

A simple number guessing game website with a **Java** backend (no frameworks, no dependencies — just the JDK).

The server picks a secret number between 1 and 100. You guess, and it tells you "higher" or "lower" until you get it. Finish a game to save your score on the Top 10 leaderboard.

## Run it

Requires JDK 11 or newer.

```bash
javac -d out src/GameServer.java
java -cp out GameServer
```

Then open <http://localhost:8080>. To use another port: `java -cp out GameServer 9000` (or set the `PORT` env var).

## Project layout

```
src/GameServer.java   Java HTTP server + game logic + leaderboard
public/index.html     Page
public/style.css      Styling
public/game.js        Browser-side game UI
scores.txt            Created automatically to store the leaderboard
```

## API

| Endpoint | Description |
|---|---|
| `GET /api/new` | Start a game, returns `{id, max}` |
| `GET /api/guess?id=…&n=…` | Returns `{result: higher\|lower\|correct, attempts}` |
| `GET /api/scores` | Top 10 scores |
| `POST /api/scores?id=…&name=…` | Save the score of a finished game |

## Hosting note

GitHub Pages only serves static files, so it can't run the Java server. To put the game online, deploy this repo to a host that runs Java (Render, Railway, Fly.io, etc.) with the commands above.
