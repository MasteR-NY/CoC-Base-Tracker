# Keeping README previews current

`app/src/test/java/com/villageclock/app/DesignPreviewTest.java` uses Robolectric API 35 native Android graphics to render the actual MainActivity, PlannerActivity and RemoteViews widget with synthetic data. These are framework-rendered examples; no phone, Supercell account or private export is required. The home-screen host and system chrome can differ on a real device.

Run normal `build.ps1` after changing UI code, layouts, colors, art or typography. The tests render stable PNG filenames into `outputs/previews/`. After tests, lint and assembly succeed, the build copies them to tracked `docs/images/` paths referenced by README. Gradle tracks the preview directory as a test output so deleting it causes regeneration. `-SkipTests` leaves previews unchanged.

Inspect all three images for clipping, legibility and selection state, then commit the images with the source change. The source ZIP includes `docs/images/` automatically. Release filenames can change without changing README image URLs.

For new screens, extend DesignPreviewTest and the build's preview copy list, then add the image to README. Keep sample data synthetic rather than exposing personal accounts.
