# Asset licensing policy

This document records which visual assets may be reused in OffHandCombat and what evidence must be retained before an image is added to a release.

## Current distributable

The current NeoForge 1.21.1 source tree and distributable JAR contain no PNG, JPEG, GIF, SVG or WebP image assets. No third-party image is currently shipped.

## Upstream repository icon

The original project contains a mod icon at:

- repository: `BunnyCinnamon/OffHandCombat`
- revision: `e7df3ad2eec858407dd371cdfde574b35d0322c4`
- path: `common/src/main/resources/assets/offhandcombat/icon.png`
- Git blob SHA: `789d1acc44cd73f93fc121524bb14bf910b39171`
- dimensions: 640 × 640
- file size: 283,453 bytes

The original Fabric metadata explicitly names this file as the project icon. The file is stored inside the upstream repository governed by its root MIT License, and no separate asset exception was found. It may therefore be copied, modified and redistributed under that MIT grant, provided the original copyright and license notice remain available in `LICENSE` and the source is recorded in `THIRD_PARTY_NOTICES.md`.

This conclusion applies to that exact repository asset and revision. It does not automatically cover visually similar files hosted elsewhere.

## Required checks before adding the upstream icon

Before the icon is included in a release:

1. obtain it from the exact repository path and revision above;
2. verify the Git blob SHA;
3. visually inspect the file for third-party logos, game textures, packaging art or other material not created or licensed by the upstream author;
4. preserve `LICENSE`, `THIRD_PARTY_NOTICES.md` and this source record;
5. add the final repository path and file checksum to `PORT_MANIFEST.sha256`;
6. confirm that the resulting project presentation does not imply official Mojang or Microsoft approval.

An MIT license cannot grant rights the upstream contributor did not own. A clean repository license is strong reuse evidence, but it is not a substitute for checking the actual artwork.

## CurseForge and other hosted images

Do not treat hosting on CurseForge, a CDN, an issue, a social-media post or another project page as a reuse license.

The following require either an exact byte-for-byte match to the MIT-licensed repository asset or separate permission from the relevant rightsholder:

- CurseForge project avatars and thumbnails;
- CurseForge gallery images and screenshots;
- images from issue comments, social media or promotional posts;
- images found only in another fork;
- Minecraft or Mojang logos, official artwork, packaging art and extracted game textures.

The uploader retains ownership of content hosted on CurseForge. The platform terms grant rights to the platform, not a general license to unrelated third parties.

## Minecraft brand and game assets

Minecraft screenshots and fan-created material are governed in addition by the current Minecraft Usage Guidelines and EULA. In particular:

- do not imply that this mod is official, approved, endorsed or supported by Mojang or Microsoft;
- do not use the official Minecraft logo or official promotional artwork as this project's logo;
- do not redistribute extracted game graphics or textures as standalone project assets;
- prefer original artwork that merely describes the mod's off-hand combat concept;
- re-check the current Usage Guidelines before publication because those permissions may change.

## Preferred release artwork

The lowest-risk option is a new original icon created specifically for this fork, with no copied Minecraft texture, official logo, third-party character or unlicensed font. Record its author, creation date and license in this document before shipping it.

## Release checklist

A release containing any image must have all of the following:

- a known author or source;
- an explicit license or written permission;
- preserved attribution and license notices;
- a checksum in `PORT_MANIFEST.sha256`;
- no misleading official-brand presentation;
- no unexplained image copied only from a hosting platform.
