# ViaBedrock Fabric Client – Minecraft 26.2

A standalone Fabric **client** mod built from the supplied patched ViaBedrock source.

It is intentionally **not** a ViaProxy server plugin and does not reuse ViaFabricPlus proxy/connection code.
The client opens a Bedrock entry from the multiplayer screen and connects directly to the Bedrock server over RakNet. The bundled ViaBedrock core performs Java 26.2 <-> Bedrock packet translation.

## Architecture

```text
Minecraft 26.2 Fabric client
        |
        +-- Multiplayer -> Bedrock
        |                  |
        |                  +-- host / port / Bedrock protocol
        |                  |
        |                  v
        |             RakNet direct
        |                  |
        |                  v
        |             ViaBedrock pipeline
        |                  |
        |          Java 26.2 <-> Bedrock
        |                  |
        |                  +-- ItemStackRequest
        |                  +-- ItemStackResponse
        |                  +-- inventory/container translation
        |                  +-- normal login/encryption/compression
        v
      Bedrock server
```

## Build

```bash
chmod +x ./gradlew
./gradlew build
```

The Fabric mod jar is written to `build/libs/`. `fabric.mod.json` and `viabedrock-fabric.mixins.json` are packaged **inside the jar**.

## Notes

* Target Minecraft: 26.2
* Java: 25
* Fabric Loader: 0.19.3
* Bedrock target: the latest Bedrock protocol registered by the bundled ViaBedrock core (currently 1.26.30 in the supplied source)
* Direct connection: yes
* ViaProxy: no
* ViaFabricPlus proxy code: no
* Inventory packet fix: included in the bundled patched ViaBedrock source

## Minecraft 26.2 build note

Minecraft 26.2 is shipped unobfuscated. Fabric Loom 1.17 uses the non-remapping `net.fabricmc.fabric-loom` plugin for this environment, so this project intentionally does not declare `loom.officialMojangMappings()` and uses the plain `jar` output.

## Bedrock Microsoft account login

The client now includes a Bedrock Microsoft account screen using MinecraftAuth 5.0.2's device-code authentication flow.

Open the Bedrock screen and press **Microsoft: Not signed in**. The login screen requests a Microsoft device code, shows the code, and provides an **Open Microsoft Login** button. After completing Microsoft/Xbox authentication, the authenticated Bedrock multiplayer token and session key are stored in:

`config/viabedrock-fabric/bedrock-account.json`

The token file is local sensitive account data and should not be shared.

Before connecting, the client refreshes the Bedrock multiplayer token and certificate chain and injects the resulting `AuthData` into ViaBedrock's Bedrock login path. ViaBedrock then sends a full authenticated Bedrock login instead of generating its anonymous/self-signed fallback identity.
