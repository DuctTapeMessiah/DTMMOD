# DTMMOD

DTMMOD is a Minecraft utility mod designed by DuctTapeMessiah with quality-of-life features.

## Current Features
- **DataLogger Module**: Tracks players entering/leaving the render range and logs direct messages to a file.

## Prerequisites
To use DTMMOD, ensure you have the following installed:
- **Minecraft**: Version 1.21.4
- **Fabric Loader**: Version 0.16.9 or higher
- **Meteor Client**: Latest version compatible with Minecraft 1.21.4
- **Baritone**: Version Version 1.13.0

## Installation
1. Install **Minecraft 1.21.4** via the Minecraft Launcher.
2. Download and install the **Fabric Loader** (version 0.16.9 or higher) from [FabricMC](https://fabricmc.net/).
3. Install **Meteor Client** (ensure compatibility with Minecraft 1.21.4) from [Meteor Client’s official site](https://meteorclient.com/).
4. Install **Baritone** (version `baritone-api-fabric-1.13.0`) by following instructions from [Baritone’s GitHub](https://github.com/cabaletta/baritone).
5. Download the latest DTMMOD `.jar` file from the [Releases](https://github.com/DuctTapeMessiah/DTMMOD/releases) page.
6. Place the DTMMOD `.jar` file in your Minecraft `mods` folder (usually located at `~/.minecraft/mods` on Windows: `%appdata%\.minecraft\mods`).
7. Launch Minecraft with the Fabric profile to load DTMMOD.

## Features
- **Farming Simulator 26**: Automates farming tasks.
    - Converting mycelium to dirt paths (with mushroom breaking)
    - Tilling blocks to farmland, and planting seeds/crops on farmland within configurable ranges.
    - Supports Baritone pathing, tool/inventory management, light level checks, and chat feedback.

- **Mapart Downloader**: Automatically detects item frames with filled maps in render distance, downloads individual map images as PNGs, and stitches connected clusters (by adjacency or name prefix) into larger images saved in DTMMOD/maps/ subfolders.
    - Stitch by cluster or by name (work-in-progress, to seperate multiple mapart in single arrays)
    - UUIDs are saved for clusters to prevent partially rendered issues. Stitch file will update if more IDs are rendered

- **DataLogger Module**: Automatically tracks players entering or leaving the render range and logs direct messages to a file located in `~/.minecraft/DTMMOD/logs`.
  - Logs are saved in a structured format for easy review.
  - Configurable settings available through the **Meteor Client** interface (if enabled).

## Contributing
Contributions are welcome! To contribute:
1. Fork this repository.
2. Create a new branch (`git checkout -b feature/your-feature`).
3. Make your changes and commit them (`git commit -m "Add your feature"`).
4. Push to your branch (`git push origin feature/your-feature`).
5. Open a pull request on this repository.

## License
This project is licensed under the [MIT License](LICENSE).

## Contact
For issues or suggestions, open an issue on this repository or contact DuctTapeMessiah on [GitHub](https://github.com/DuctTapeMessiah).
