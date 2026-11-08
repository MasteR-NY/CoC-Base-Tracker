# Keeping README Previews Current

`app/src/test/java/com/villageclock/app/DesignPreviewTest.java` uses Robolectric API 35 native Android graphics to render the actual MainActivity, PlannerActivity, What’s New guide and RemoteViews widget with synthetic data. These are framework-rendered examples; no phone, Supercell account or private export is required. The home-screen host and system chrome can differ on a real device.

For readable widget previews, the test host uses a fixed in-memory collection containing the same row renderer as the foreground service. This lets native Android render the actual d/h/m/s layout without binding a continuously running launcher service in the screenshot test.

Run normal `build.ps1` after changing UI code, layouts, colors, art or typography. The tests render stable PNG filenames into `outputs/previews/`. After tests, lint and assembly succeed, the build copies them to tracked `docs/images/` paths referenced by README. Gradle tracks the preview directory as a test output so deleting it causes regeneration. `-SkipTests` leaves previews unchanged.

Inspect all nine images for clipping, legibility and selection state, then commit the images with the source change. The normal app preview shows the home screen after acknowledging the release summary. The guide and Overview tabs have separate images. The source ZIP includes `docs/images/` automatically. Release filenames can change without changing README image URLs.

For new screens, extend DesignPreviewTest and the build's preview copy list, then add the image to README. Keep sample data synthetic rather than exposing personal accounts.
