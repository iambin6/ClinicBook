# Backend on Render Free (512 MiB)

The Docker image activates the `render` profile and listens on `0.0.0.0:$PORT`.
It caps the Java heap at 50% of container memory and uses C1 compilation to
reduce startup work on a small CPU allocation. Tomcat and the database pool
also use smaller defaults. The application retains normal eager initialization:
JPA and repositories are verified before `/health` becomes ready.
Local Maven/IDE runs keep the existing configuration.
Unused Gemini SDKs are removed because `GeminiClient` uses Java's HTTP client;
Selenium is included only when compiling/running tests.
The image also extracts the executable JAR at build time so runtime class loading
uses regular library JARs instead of the nested executable-JAR loader.

## Render configuration

- Keep the backend Dockerfile as the start configuration, with Docker Command empty.
- Keep the existing database, OAuth, mail and chatbot environment variables.
- Set `/health` as the HTTP health check path. It returns 200 only after Spring
  reports that the application is ready to accept requests.
- Avoid custom `-Xmx`/`-Xms` flags in `JAVA_TOOL_OPTIONS` or `JDK_JAVA_OPTIONS`
  that conflict with the image's memory settings.
- The startup log should show the active `render` profile and `Tomcat started`.

Local secret files are excluded from the Docker build; production credentials
must come from Render's environment settings.

## Reproduce startup locally

```powershell
docker build -t clinic-booking:render-startup-fix .
./scripts/Test-RenderStartup.ps1
```

The script runs the image with 512 MiB RAM, no swap and 0.1 CPU. It creates a
fresh local MySQL database using synthetic credentials and checks both
`/specialties/all` and `/health`. It reports listener/readiness times and sampled peak memory,
then removes its disposable containers and database volume. It does not connect
to the production database or use real API keys.

Validated locally with 512 MiB, no swap and 0.1 CPU: both endpoints returned 200,
with sampled peak memory around 265 MiB and readiness after about three minutes
against a fresh MySQL database. Cloud CPU availability and database latency can
change startup time.
