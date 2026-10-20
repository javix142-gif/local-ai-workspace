# QA ARM64 run 39511660726

- Tested SHA: `01161491acc2a7c836222a84b7bd6f778292f8f3`; official QA gate waited for the matching successful instrumentation run 39511660788.
- Result: PASS; build command `:app:clean :app:assembleDebug -Parm64Only=true`, Gradle 8.10.2/JDK 17, SDK 35, NDK 27.0.12077973, CMake 3.31.6; `BUILD SUCCESSFUL` in 5m39s.
- APK: `com.localai.workspace.debug`, version `0.5.0-debug`/27, ARM64 only, 121,582,979 bytes, SHA-256 `2dc3dd24992d2878099e67c2117bbc711fd1bce96ab610be4ba8a35ab7de6b6f`.
- `unzip -t`, `zipalign -c -v 4`, package/version and `apksigner` v2 all passed. Certificate SHA-256: `b0d1c40838a22b6bbfc7bfa176c9bb917c7194c7b74dedf749566fb268ab120b`. No model weight payloads found; the only `.bin` is the Kotlin `DebugProbesKt.bin` debug resource.
- GitHub artifact `12307042341`, 48,959,437 bytes, SHA-256 `313faccd96667f3d0977dcfc676dbd9701f432a3e4fd0b678df19d66c30b617e`; retention through 2026-10-27. The APK itself is not committed to Git.
- Drive delivery contains only this APK inside a ZIP wrapper (the connector rejects direct uploads over 100 MiB): [download](https://drive.google.com/file/d/1adOGxZnyyePC_CY68ZMWWBjy6mKjIWt9/view?usp=drivesdk). ZIP SHA-256 `1146cacfc2989b4cd22c208af004f09acef209873437dd7f145c9bb1a33cc8b8`; Drive readback compared byte-for-byte.
- QA Debug only, not a Release or physical-device validation. `EMULATOR=PASS` for the paired run; `PHYSICAL_DEVICE=NOT_RUN`.
