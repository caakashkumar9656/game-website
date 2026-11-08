let gameId = null;
let finished = false;

const $ = (id) => document.getElementById(id);

async function newGame() {
  const res = await fetch("/api/new");
  const data = await res.json();
  gameId = data.id;
  finished = false;
  $("max").textContent = data.max;
  $("guess").max = data.max;
  $("guess").value = "";
  $("guess").disabled = false;
  $("message").textContent = "Make your first guess!";
  $("message").className = "message";
  $("attempts").textContent = "0";
  $("win").hidden = true;
  $("play").hidden = false;
  $("guess").focus();
}

async function submitGuess(e) {
  e.preventDefault();
  if (finished) return;
  const n = $("guess").value;
  const res = await fetch(`/api/guess?id=${encodeURIComponent(gameId)}&n=${encodeURIComponent(n)}`);
  const data = await res.json();
  const msg = $("message");

  if (data.error) {
    msg.textContent = data.error;
    msg.className = "message lower";
    return;
  }

  $("attempts").textContent = data.attempts;
  if (data.result === "correct") {
    finished = true;
    msg.textContent = `🎉 Correct! You got it in ${data.attempts} ${data.attempts === 1 ? "try" : "tries"}.`;
    msg.className = "message correct";
    $("guess").disabled = true;
    $("win").hidden = false;
    $("name").focus();
  } else {
    msg.textContent = data.result === "higher" ? "⬆️ Too low — go higher!" : "⬇️ Too high — go lower!";
    msg.className = "message " + data.result;
  }
  $("guess").value = "";
  $("guess").focus();
}

async function saveScore(e) {
  e.preventDefault();
  const name = $("name").value;
  const res = await fetch(`/api/scores?id=${encodeURIComponent(gameId)}&name=${encodeURIComponent(name)}`, { method: "POST" });
  if (res.ok) {
    renderScores(await res.json());
    $("win").hidden = true;
  }
}

function renderScores(list) {
  const ol = $("scores");
  ol.innerHTML = "";
  if (list.length === 0) {
    ol.innerHTML = "<li>No scores yet — be the first!</li>";
    return;
  }
  for (const s of list) {
    const li = document.createElement("li");
    li.textContent = s.name;
    const span = document.createElement("span");
    span.textContent = s.attempts + " tries";
    li.appendChild(span);
    ol.appendChild(li);
  }
}

async function loadScores() {
  const res = await fetch("/api/scores");
  renderScores(await res.json());
}

$("guessForm").addEventListener("submit", submitGuess);
$("scoreForm").addEventListener("submit", saveScore);
$("newGame").addEventListener("click", newGame);

newGame();
loadScores();
