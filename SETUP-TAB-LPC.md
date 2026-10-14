# Hooking NickBook into PlaceholderAPI, LuckPerms, LPC and TAB

> Since 1.2.0 NickBook has its own chat (chat.yml) and tab (tab.yml) system.
> You only need LPC/TAB if you prefer them. With TAB installed NickBook's tab module turns off;
> with LPC installed (defer-to-lpc: true) LPC builds the chat format.

Install: PlaceholderAPI, LuckPerms, LPC, TAB, NickBook. Then run:
/papi ecloud download LuckPerms
/papi reload

## Placeholders
| Placeholder | When nicked | When not nicked |
|---|---|---|
| %nickbook_prefix% | fake rank, e.g. [VIP+] | real LuckPerms prefix |
| %nickbook_name% | nick name | real username |
| %nickbook_suffix% | empty | real LuckPerms suffix |
| %nickbook_displayname% | prefix+name+suffix | prefix+name+suffix |
| %nickbook_is_nicked% | true | false |
| %nickbook_rank% | nick rank key | empty |
| %nickbook_group% | nicked-group | chat group |

## LPC (plugins/LPC/config.yml)
Replace {prefix}/{name}/{suffix} with the NickBook placeholders:

chat-format: "%nickbook_prefix%%nickbook_name%%nickbook_suffix%&f: {message}"

If you use group-formats, change every group the same way. Then /lpc reload.
With `defer-to-lpc: true` (chat.yml) NickBook does NOT touch the chat format when LPC is installed, so ranks stay intact.

## TAB (plugins/TAB/groups.yml)
_DEFAULT_:
  tabprefix: "%nickbook_prefix%"
  customtabname: "%nickbook_name%"
  tabsuffix: "%nickbook_suffix%"
  tagprefix: "%nickbook_prefix%"
  tagsuffix: "%nickbook_suffix%"

Remove tabprefix/tagprefix lines from other groups (or set them to the same values),
otherwise those groups override the nick. Then /tab reload.

Optional, faster refresh (plugins/TAB/config.yml):
placeholder-refresh-intervals:
  "%nickbook_prefix%": 500
  "%nickbook_name%": 500
  "%nickbook_suffix%": 500

## Action bar
While nicked, above the hearts/hunger bar (config.yml `nicked-actionbar`): &fYou are currently &cNICKED
