
// ========================================
// QUIZ ARENA - JAVASCRIPT
// ========================================

let waitingInterval;
let questionTimerInterval;
let resultInterval;

let currentQuestion = 0;
let score = 0;
let questionLocked = false;
let currentPlayerName = "";
let quizStartTime = 0;
let quizHasStarted = false;
let timeLeft = 30;


// ========================================
// QUIZ QUESTIONS
// ========================================

const quizQuestions = [
    {
        question: "Which planet is known as the Red Planet?",
        options: ["Earth", "Mars", "Jupiter", "Venus"],
        answer: 1
    },
    {
        question: "What is the capital of India?",
        options: ["Mumbai", "Chennai", "New Delhi", "Kolkata"],
        answer: 2
    },
    {
        question: "Which language is used to create web page structure?",
        options: ["HTML", "Java", "Python", "C"],
        answer: 0
    },
    {
        question: "How many sides does a hexagon have?",
        options: ["Five", "Six", "Seven", "Eight"],
        answer: 1
    },
    {
        question: "Which is the largest ocean?",
        options: ["Atlantic", "Indian", "Arctic", "Pacific"],
        answer: 3
    }
];


// ========================================
// SCREEN NAVIGATION
// ========================================

function showRegistration() {

    document.getElementById("homeScreen").hidden = true;
    document.getElementById("registrationScreen").hidden = false;

    document.getElementById("playerName").value = "";
    document.getElementById("message").textContent = "";
}

function goHome() {

    document.getElementById("registrationScreen").hidden = true;
    document.getElementById("homeScreen").hidden = false;
}


// ========================================
// PLAYER REGISTRATION
// ========================================

async function registerPlayer() {

    const playerName =
        document.getElementById("playerName").value.trim();

    const message =
        document.getElementById("message");

    if (playerName === "") {
        message.textContent = "Please enter your player name!";
        return;
    }

    if (playerName.length < 3) {
        message.textContent =
            "Name must contain at least 3 characters.";
        return;
    }

    try {

        const response = await fetch("/api/join", {
            method: "POST",

            headers: {
                "Content-Type": "application/x-www-form-urlencoded"
            },

            body: new URLSearchParams({
                playerName: playerName
            })
        });

        const result = await response.json();

        if (!result.success) {
            message.textContent = result.message;
            return;
        }

        currentPlayerName = playerName;

        document.getElementById("waitingName").textContent =
            playerName;

        document.getElementById("registrationScreen").hidden = true;
        document.getElementById("waitingScreen").hidden = false;

        document.getElementById("waitingMessage").textContent =
            "Players joined: " + result.playerCount + "/2";

        clearInterval(waitingInterval);

        checkRoomStatus();

        waitingInterval = setInterval(checkRoomStatus, 1500);

    } catch (error) {

        message.textContent =
            "Cannot connect to the game server!";

        console.log("Registration error:", error);
    }
}


// ========================================
// CHECK ROOM STATUS
// ========================================

function checkRoomStatus() {

    fetch("/api/status")

        .then(response => response.json())

        .then(data => {

            document.getElementById("waitingMessage").textContent =
                data.started
                    ? "Both players joined! Starting quiz..."
                    : "Players joined: " + data.playerCount + "/2. Waiting for Player 2...";

            if (data.started && !quizHasStarted) {

                quizHasStarted = true;

                clearInterval(waitingInterval);

                document.getElementById("waitingScreen").hidden = true;
                document.getElementById("quizScreen").hidden = false;

                currentQuestion = 0;
                score = 0;
                questionLocked = false;
                quizStartTime = Date.now();

                showQuestion();
            }
        })

        .catch(error => {
            console.log("Could not check room status:", error);
        });
}


// ========================================
// DISPLAY QUESTIONS
// ========================================

function showQuestion() {

    questionLocked = false;
    timeLeft = 30;

    clearInterval(questionTimerInterval);

    const question = quizQuestions[currentQuestion];

    document.getElementById("questionNumber").textContent =
        currentQuestion + 1;

    document.getElementById("questionText").textContent =
        question.question;

    document.getElementById("quizMessage").textContent = "";

    document.getElementById("timer").textContent = timeLeft;

    const answerOptions =
        document.getElementById("answerOptions");

    answerOptions.innerHTML = "";

    question.options.forEach((option, index) => {

        const button = document.createElement("button");

        button.textContent = option;
        button.className = "answer-option";

        button.onclick = function () {
            selectAnswer(index);
        };

        answerOptions.appendChild(button);
    });

    questionTimerInterval = setInterval(() => {

        timeLeft--;

        document.getElementById("timer").textContent = timeLeft;

        if (timeLeft <= 0) {
            selectAnswer(-1);
        }

    }, 1000);
}


// ========================================
// CHECK ANSWER
// ========================================

function selectAnswer(selectedIndex) {

    if (questionLocked) {
        return;
    }

    questionLocked = true;

    clearInterval(questionTimerInterval);

    const correctAnswer =
        quizQuestions[currentQuestion].answer;

    const buttons =
        document.querySelectorAll(".answer-option");

    buttons.forEach(button => {
        button.disabled = true;
    });

    if (selectedIndex === correctAnswer) {

        score++;

        document.getElementById("quizMessage").textContent =
            "Correct answer!";

    } else if (selectedIndex === -1) {

        document.getElementById("quizMessage").textContent =
            "Time's up!";

    } else {

        document.getElementById("quizMessage").textContent =
            "Incorrect answer!";
    }

    setTimeout(() => {

        currentQuestion++;

        if (currentQuestion < quizQuestions.length) {

            showQuestion();

        } else {

            finishQuiz();
        }

    }, 1000);
}


// ========================================
// FINISH QUIZ
// ========================================

async function finishQuiz() {

    clearInterval(questionTimerInterval);

    const timeTaken = Math.floor(
        (Date.now() - quizStartTime) / 1000
    );

    document.getElementById("quizScreen").hidden = true;
    document.getElementById("leaderboardScreen").hidden = false;

    document.getElementById("winnerName").textContent =
        "Waiting for Player 2...";

    document.getElementById("winnerMessage").textContent =
        "Your score: " + score + "/" + quizQuestions.length;

    document.getElementById("leaderboardList").textContent =
        "Waiting for both players to finish...";

    try {

        const response = await fetch("/api/finish", {
            method: "POST",

            headers: {
                "Content-Type": "application/x-www-form-urlencoded"
            },

            body: new URLSearchParams({
                playerName: currentPlayerName,
                score: score,
                time: timeTaken
            })
        });

        const result = await response.json();

        if (result.success) {
            checkFinalResults();
        } else {
            document.getElementById("leaderboardList").textContent =
                "Could not submit your score.";
        }

    } catch (error) {

        console.log("Score submission error:", error);
    }
}


// ========================================
// CHECK FINAL RESULTS
// ========================================

function checkFinalResults() {

    clearTimeout(resultInterval);

    fetch("/api/results")

        .then(response => response.json())

        .then(data => {

            if (!data.ready) {

                resultInterval = setTimeout(checkFinalResults, 1500);
                return;
            }

            const players = data.players.sort((a, b) =>
                b.score - a.score || a.time - b.time
            );

            const winner = players[0];

            document.getElementById("winnerName").textContent =
                "🏆 " + winner.name + " 🏆";

            document.getElementById("winnerMessage").textContent =
                "🎉 CHAMPION! CONGRATULATIONS! 🎉";

            document.getElementById("leaderboardList").innerHTML = `
                <table class="result-table">
                    <thead>
                        <tr>
                            <th>Rank</th>
                            <th>Player</th>
                            <th>Score</th>
                            <th>Time</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${players.map((p, index) => `
                            <tr class="${index === 0 ? 'first-place' : 'second-place'}">
                                <td>${index === 0 ? '🥇 1' : '🥈 2'}</td>
                                <td>${escapeHTML(p.name)}</td>
                                <td>${p.score}/5</td>
                                <td>${p.time}s</td>
                            </tr>
                        `).join("")}
                    </tbody>
                </table>

                <div style="text-align:center; margin-top:25px;">
                    <button onclick="leaveGameAndGoHome()"
                        style="
                            background: linear-gradient(135deg, #f5c542, #ff9800);
                            color: #10172f;
                            padding: 14px 30px;
                            border: none;
                            border-radius: 12px;
                            font-size: 17px;
                            font-weight: bold;
                            cursor: pointer;
                        ">
                        🏠 Back to Home
                    </button>
                </div>
            `;

            createConfetti();

        })

        .catch(error => {

            console.log("Results error:", error);

            resultInterval = setTimeout(checkFinalResults, 2000);
        });
}


// ========================================
// RETURN HOME AND LEAVE MATCH
// ========================================

async function leaveGameAndGoHome() {

    clearInterval(waitingInterval);
    clearInterval(questionTimerInterval);
    clearTimeout(resultInterval);

    try {

        await fetch("/api/leave", {
            method: "POST",

            headers: {
                "Content-Type": "application/x-www-form-urlencoded"
            },

            body: new URLSearchParams({
                playerName: currentPlayerName
            })
        });

    } catch (error) {

        console.log("Leave error:", error);
    }

    currentPlayerName = "";
    quizHasStarted = false;

    document.getElementById("leaderboardScreen").hidden = true;
    document.getElementById("homeScreen").hidden = false;
}


// ========================================
// HTML ESCAPE
// ========================================

function escapeHTML(value) {

    return String(value).replace(/[&<>"']/g, char => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    })[char]);
}


// ========================================
// CONFETTI
// ========================================

function createConfetti() {

    for (let i = 0; i < 60; i++) {

        const piece = document.createElement("span");

        piece.className = "confetti-piece";
        piece.style.left = Math.random() * 100 + "vw";
        piece.style.animationDelay = Math.random() * 3 + "s";

        piece.style.backgroundColor =
            ["#FFD700", "#FF4D6D", "#00D4FF", "#7CFC00", "#B76EFF"]
            [Math.floor(Math.random() * 5)];

        document.body.appendChild(piece);

        piece.addEventListener("animationend", () => {
            piece.remove();
        });
    }
}


// ========================================
// SERVICE WORKER
// ========================================

if ("serviceWorker" in navigator) {

    window.addEventListener("load", () => {

        navigator.serviceWorker.register("/service-worker.js")

            .then(() => console.log("Quiz Arena PWA is ready!"))

            .catch(error => console.log("PWA error:", error));
    });
}
