const API_BASE = "http://localhost:8081/api";

const state = {
  sessionId: null,
  secretLength: null,
  maxAttempts: null,
  guessCount: 0,
};

const newGameForm = document.getElementById("new-game-form");
const guessForm = document.getElementById("guess-form");
const guessInput = document.getElementById("guess-input");
const gameSection = document.getElementById("game-section");
const infoLength = document.getElementById("info-length");
const infoAttempts = document.getElementById("info-attempts");
const statusLine = document.getElementById("status-line");
const historyBody = document.getElementById("history-body");
const errorLine = document.getElementById("error-line");

newGameForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  clearError();

  const name = document.getElementById("player-name").value.trim() || "Player";

  try {
    const res = await fetch(`${API_BASE}/new`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ playerName: name }),
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || "Failed to start game");

    state.sessionId = data.sessionId;
    state.secretLength = data.secretLength;
    state.maxAttempts = data.maxAttempts;
    state.guessCount = 0;

    infoLength.textContent = `Secret length: ${data.secretLength}`;
    infoAttempts.textContent = `Max attempts: ${data.maxAttempts}`;

    guessInput.maxLength = data.secretLength;
    guessInput.value = "";
    historyBody.innerHTML = "";
    setStatus("IN_PROGRESS", 0, data.maxAttempts);
    setGuessFormEnabled(true);
    gameSection.classList.remove("hidden");
    guessInput.focus();
  } catch (err) {
    showError(err.message);
  }
});

guessForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  clearError();

  const guess = guessInput.value.trim();
  if (!guess) return;

  try {
    const res = await fetch(`${API_BASE}/guess`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ sessionId: state.sessionId, guess }),
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || "Guess failed");

    state.guessCount += 1;
    addHistoryRow(state.guessCount, guess, data.deadCount, data.woundedCount);
    setStatus(data.status, data.attemptsUsed, data.maxAttempts);

    if (data.status !== "IN_PROGRESS") {
      setGuessFormEnabled(false);
    } else {
      guessInput.value = "";
      guessInput.focus();
    }
  } catch (err) {
    showError(err.message);
  }
});

function setGuessFormEnabled(enabled) {
  guessInput.disabled = !enabled;
  guessForm.querySelector("button").disabled = !enabled;
}

function addHistoryRow(index, guess, dead, wounded) {
  const row = document.createElement("tr");
  row.innerHTML = `<td>${index}</td><td>${guess}</td><td>${dead}</td><td>${wounded}</td>`;
  historyBody.appendChild(row);
}

function setStatus(status, attemptsUsed, maxAttempts) {
  const labels = {
    IN_PROGRESS: `In progress — attempt ${attemptsUsed}/${maxAttempts}`,
    WON: `You won! Solved in ${attemptsUsed} attempt(s).`,
    LOST: `Out of attempts (${attemptsUsed}/${maxAttempts}). Better luck next time.`,
  };
  statusLine.textContent = labels[status] || status;
  statusLine.className = status.toLowerCase().replace("_", "-");
}

function showError(message) {
  errorLine.textContent = message;
  errorLine.classList.remove("hidden");
}

function clearError() {
  errorLine.textContent = "";
  errorLine.classList.add("hidden");
}
