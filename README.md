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

Tasks, calendar and workouts save to a local H2 file under `data/`.

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

## Login

Home, About, and Projects are public. Tasks, Calendar, Workout, and `/projects/manage` need a login
(footer link "log in", or go to `/login`).

The username and BCrypt password hash live in `config/application.properties`, which is git-ignored
so it never reaches GitHub:

```properties
spring.security.user.name=evangelos
spring.security.user.password={bcrypt}<hash>
```

On a fresh clone without that file, Spring Boot prints a random one-time password in the startup log
(username `user`).
