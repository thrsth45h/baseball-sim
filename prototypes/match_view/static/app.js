"use strict";
const token = document.querySelector('meta[name="match-token"]').content;
const byId = id => document.getElementById(id);
let state = null;
let busy = false;

function lock(value) {
  busy = value;
  document.querySelectorAll("button, input").forEach(element => { element.disabled = value; });
  byId("progress").disabled = value || !state || state.done;
  document.querySelector("main").setAttribute("aria-busy", String(value));
}

function countDots(id, count, capacity, label) {
  const target = byId(id);
  target.replaceChildren();
  for (let i = 0; i < capacity; i += 1) {
    const dot = document.createElement("span");
    dot.className = "dot" + (i < count ? " on" : "");
    dot.setAttribute("aria-hidden", "true");
    target.append(dot);
  }
  target.setAttribute("aria-label", label + count);
  byId(id + "-number").textContent = count;
}

function render(data) {
  state = data;
  byId("seed").value = data.seed;
  byId("away-score").textContent = data.score.away;
  byId("home-score").textContent = data.score.home;
  byId("pitch-total").textContent = data.pitches_accepted + "球";
  byId("pa-total").textContent = "完了打席 " + data.completed_pa;
  byId("phase").textContent = data.current ? data.current.half_text : (data.status === "completed" ? "試合終了" : "未完了");
  byId("current").hidden = data.done;
  byId("outcome").hidden = !data.done;
  byId("ending-reason").hidden = !data.done;
  byId("outcome").textContent = data.outcome || "";
  byId("ending-reason").textContent = data.reason || "";
  byId("walkoff-note").hidden = !data.line_score.walkoff;
  for (const team of ["away", "home"]) {
    const row = byId(team + "-line");
    row.querySelectorAll("td").forEach(cell => cell.remove());
    [...data.line_score[team], data.score[team]].forEach((value, index) => {
      const cell = document.createElement("td");
      cell.textContent = value === null ? "—" : value;
      if (data.current && index === data.current.inning - 1 && team === data.current.team) cell.className = "active-half";
      row.append(cell);
    });
  }
  if (data.current) {
    const current = data.current;
    byId("batter-slot").textContent = current.slot + "番";
    byId("batter-name").textContent = current.batter_name;
    byId("batting-side").textContent = (current.team === "away" ? "仮Away" : "仮Home") + "の攻撃";
    countDots("balls", current.balls, 4, "ボール");
    countDots("strikes", current.strikes, 3, "ストライク");
    countDots("outs", current.outs, 3, "アウト");
    const occupied = [];
    current.bases.forEach((value, i) => {
      document.querySelector('[data-base="' + (i + 1) + '"]').classList.toggle("occupied", value);
      if (value) occupied.push((i + 1) + "塁");
    });
    byId("bases").setAttribute("aria-label", occupied.length ? "走者 " + occupied.join("・") : "走者なし");
  }
  byId("recent").replaceChildren();
  for (const event of [...data.recent].reverse()) {
    const row = document.createElement("li");
    for (const [text, className] of [[event.pitch + "球", "pitch-badge"], [event.text, "play-text"], [event.detail, "play-detail"]]) {
      if (!text) continue;
      const item = document.createElement("span"); item.className = className; item.textContent = text; row.append(item);
    }
    byId("recent").append(row);
  }
  if (!data.recent.length) {
    const empty = document.createElement("li"); empty.className = "empty"; empty.textContent = "「1球」または「1打席」で試合を始めます。"; byId("recent").append(empty);
  }
  byId("announcement").textContent = [data.current ? data.current.half_text + " " + data.current.batter_name : data.outcome, "仮Away " + data.score.away + " 対 仮Home " + data.score.home, data.pitches_accepted + "球"].join("、");
}

async function request(command = null, seed = null) {
  if (busy) return;
  lock(true); byId("error").hidden = true;
  try {
    const options = command ? {method: "POST", headers: {"Content-Type": "application/json", "X-Match-Token": token}, body: JSON.stringify(command === "new" ? {command, seed} : {command})} : {};
    const response = await fetch(command ? "/api/command" : "/api/state", options);
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || "通信に失敗しました。");
    render(data);
  } catch (error) {
    byId("error").textContent = error.message || "通信に失敗しました。"; byId("error").hidden = false;
  } finally { lock(false); }
}

document.querySelectorAll("[data-command]").forEach(button => button.addEventListener("click", () => request(button.dataset.command)));
byId("new-game").addEventListener("submit", event => {
  event.preventDefault();
  if (busy) return;
  const seed = byId("seed").value;
  if (!/^(0|[1-9][0-9]{0,19})$/.test(seed) || BigInt(seed) >= (1n << 64n)) {
    byId("error").textContent = "seedは0以上2^64未満の整数で入力してください。"; byId("error").hidden = false; return;
  }
  request("new", seed);
});
request();
