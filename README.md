# NickBook 1.2.0

Nicknames (written-book flow) + full **chat** and **tab** system for Spigot/Paper 1.8.8+ (Java 8+, works up to modern versions).

## Build
```bash
mvn clean package
```
Jar: `target/nickbook-1.2.0.jar`

## Files
| File | What |
|---|---|
| `config.yml` | random names, nick ranks, nicked action bar, server name/time format |
| `chat.yml` | chat formats per group, hover/click, mentions, moderation, join/quit, PMs, announcer |
| `tab.yml` | header/footer, tab names, nametags, sorting, objectives, animations |
| `players.yml` | player data (auto-migrated from old config.yml) |

`/nickbook reload` reloads all three config files.

## Colors
`&a &l ...`, hex `&#FF5555` / `{#FF5555}` (1.16+), `<gradient:#FF5555:#FFAA00>text</gradient>`, `<rainbow>text</rainbow>`.
On 1.8-1.15 hex and gradients fall back to the nearest normal color automatically.

## Placeholders
All PlaceholderAPI placeholders, plus built-ins (nick aware):
`{player} {name} {displayname} {prefix} {suffix} {group} {world} {online} {max} {ping} {health} {maxhealth} {food} {level} {gamemode} {x} {y} {z} {time} {date} {tps} {server} {nicked} {channel} {animation:<name>}`

PlaceholderAPI expansion: `%nickbook_name% %nickbook_prefix% %nickbook_suffix% %nickbook_displayname% %nickbook_realname% %nickbook_is_nicked% %nickbook_rank% %nickbook_group% %nickbook_chat_muted% %nickbook_animation_<name>%`

Player chat messages are never parsed for placeholders.

## Chat features (chat.yml)
- Per group formats (LuckPerms primary group or `nickbook.group.<name>` permission), per group message color
- Nicked players use `nicked-group`, so real staff ranks never leak
- Optional hover tooltip + click action (suggest / run / url / copy) on names
- Mentions (by nick name) with highlight, sound and action bar
- Cooldown, anti-repeat (similarity %), anti-caps, word filter, anti-advertising (IPs/domains + whitelist)
- Range (local) chat with global prefix, or per-world chat
- Join / quit / first-join messages, MOTD, silent join permission
- `/msg` `/reply` `/socialspy` `/msgtoggle` `/ignore` (all nick aware), `/clearchat`, `/mutechat`, `/broadcast`
- Auto broadcast (sequential/random, multi-line entries)
- If **LPC** is installed and `defer-to-lpc: true`, LPC builds the format and NickBook still handles everything else

## Tab features (tab.yml)
- Animated header/footer, per-world overrides
- Tab name format per group, nametag prefix/suffix per group (1.13+ team color handled)
- Tab sorting by group (`sort`) + alphabetical
- Yellow number objective (ping/health/level/placeholder) and below-name objective
- Only changed values are sent, so a fast update interval is cheap
- If the **TAB** plugin is installed the module turns itself off (`disable-if-tab-plugin-installed`)

## Permissions
| Permission | Default | |
|---|---|---|
| nickbook.use | true | /nick |
| nickbook.msg / ignore / msgtoggle | true | private messages |
| nickbook.chat.color / format / magic / hex | op | colors in chat and PMs |
| nickbook.chat.mention / global | true | mention players / use global prefix |
| nickbook.chat.bypass.* | op | cooldown, repeat, caps, filter, advertising |
| nickbook.chat.notify | op | advertising alerts |
| nickbook.chat.spy | false | see range/world chat everywhere |
| nickbook.socialspy | op | /socialspy |
| nickbook.clearchat (+ .bypass) | op | |
| nickbook.mutechat (+ .bypass) | op | |
| nickbook.broadcast | op | |
| nickbook.seenicks | op | find nicked players by real name |
| nickbook.msg.bypass | op | message players with PMs off |
| nickbook.ignore.exempt | op | can't be ignored |
| nickbook.silentjoin | false | no join/quit message |
| nickbook.group.<group> | - | group when `group-source: permission` |
| nickbook.admin | op | /nickbook reload |

## Notes
It cannot control Hypixel's real nick system, and the skin choices are stored preferences rather than actual client skin replacement.
