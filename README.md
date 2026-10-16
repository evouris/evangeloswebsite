# evangeloswebsite

Personal site: Spring Boot serves Thymeleaf HTML. You style it with HTML, CSS, and Tailwind.

## Run

In one terminal:

```bash
npm install
npm run watch:css
```

In another:

```bash
./mvnw spring-boot:run
```

On Windows you can use `mvnw.cmd spring-boot:run` instead. If that fails, set `JAVA_HOME` to your JDK (for example `C:\Program Files\Java\jdk-27`) and run Maven from there.

Open [http://localhost:8080](http://localhost:8080).

## Where to edit

- HTML: `src/main/resources/templates/`
- CSS / Tailwind: `src/main/resources/static/css/input.css` (compiled to `app.css`)

Shared nav lives in `templates/layout.html`. To add a page later, add a controller method, a template, and a nav link.

Tasks, calendar, workouts and notes save to a local H2 file under `data/` (recordings go in `data/recordings/`).

## Workout tracker

`/workouts` (the **Workout** tab, login required) logs gym sessions:

- Start an empty workout, from a template (Push, Pull, Legs, Upper, Lower, Full Body), or **Repeat** a past one.
- Each exercise has a row per set with weight/reps (or reps only, or minutes/distance for cardio) and a check box.
  Checking a set saves in the background and starts a rest timer (length picked at the bottom of the page).
- Adding an exercise pre-fills the sets from the last time you did it, and the "previous" column shows those numbers.
  Beating your best set flags it as a **PR**. Tap a set number to mark it as a warm-up.
- **Exercises** lists 130+ built-in movements plus any you add. Click one for its records, a progress chart and history.

Built-in exercises live in `workouts/ExerciseSeeder.java`; add a line there and it shows up on the next start.
Templates are in `WorkoutService.PRESETS`.

## Notes

`/notes` (the **Notes** tab, login required) is a note-taker with voice recording:

- **Folders** in the sidebar (chips on a phone). Make one with "New folder…", rename or delete it under
  "Folder options". Deleting a folder keeps its notes; they move to **Unfiled**. You can also **pin** notes and
  **search** across titles, text, transcripts and summaries.
- **Typing** saves by itself a moment after you stop, and right away if you switch apps on your phone.
- **Record** uses the microphone of whatever device you're on. On a phone, the browser asks for mic access the
  first time; say Allow. The screen stays on while recording, because locking it can pause the mic. Pause/resume
  works, and if an upload fails the recording stays on the page with **Retry** and **Download** buttons.
  You can also upload an audio file (for example from Voice Memos).
- **Transcripts**: with **Live transcript** ticked, the browser writes down what it hears while you record
  (free; Chrome and Safari). If the server has a transcription key, every recording is also transcribed after
  upload, which is more accurate, and each recording gets a **Transcribe** button. You can edit any transcript.
- **Summarize** turns the typed notes plus all transcripts into a summary, key points and action items.
  With `ANTHROPIC_API_KEY` set, Claude writes it; without a key, a simple built-in summarizer does.
- **Export** downloads the note, summary and transcripts as a Markdown file.

Recording only works over `https://` (or `localhost`); browsers block the mic on plain `http://`.
Railway serves the site over https, so recording from your phone works there.

Optional keys, set as environment variables (Railway → your service → Variables):

| Variable | What it does |
| --- | --- |
| `ANTHROPIC_API_KEY` | AI summaries with Claude (get a key at console.anthropic.com) |
| `TRANSCRIPTION_API_KEY` | Transcribes recordings after upload. An OpenAI key works as-is. |
| `TRANSCRIPTION_BASE_URL`, `TRANSCRIPTION_MODEL` | For Groq (cheaper and faster): `https://api.groq.com/openai/v1` and `whisper-large-v3-turbo` |

Transcription services accept files up to 25 MB, which is roughly an hour of recording.

## Login

Home, About, and Projects are public. Tasks, Calendar, Workout, Notes, and `/projects/manage` need a login
(footer link "log in", or go to `/login`).

The username and BCrypt password hash live in `config/application.properties`, which is git-ignored
so it never reaches GitHub:

```properties
spring.security.user.name=evangelos
spring.security.user.password={bcrypt}<hash>
```

On a fresh clone without that file, Spring Boot prints a random one-time password in the startup log
(username `user`).
