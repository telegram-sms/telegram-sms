# Telegram SMS

<div align="center">
<img src="https://github.com/user-attachments/assets/7a283d15-52fe-42cd-a782-4984427db234" alt="Telegram SMS">
</div>

![Min Android Version](https://img.shields.io/badge/Min%20Android%20Version-6.0-orange.svg?style=flat-square)
[![License](https://img.shields.io/badge/License-BSD%203--Clause-blue.svg?style=flat-square)](https://github.com/telegram-sms/telegram-sms/blob/master/LICENSE)
[![GitHub Releases](https://img.shields.io/github/downloads/telegram-sms/telegram-sms/latest/app-release.apk?style=flat-square)](https://github.com/telegram-sms/telegram-sms/releases/latest)
![GitHub Sponsors](https://img.shields.io/github/sponsors/qwe7002?style=flat-square)


## News, Questions and Contributions

You can follow the Telegram channel Telegram SMS Change Log for the latest news. [English](https://t.me/tg_sms_changelog_eng), [简体中文](https://t.me/tg_sms_changelog)

**For bug reports and feature requests, please use [GitHub Issues](https://github.com/telegram-sms/telegram-sms/issues).**

The primary language used for commit messages is Simplified Chinese. However, you're welcome to use English in commit messages when making contributions.

If you want to generate the configuration QR code in a fast way, please visit [config.telegram-sms.com](https://config.telegram-sms.com).

## Get the Right Version

**Warning**: All versions are not compatible (not signed by the same key)! You have to uninstall one first to install another, which will delete all your data.

[Latest Release Download](https://github.com/telegram-sms/telegram-sms/releases/latest)

[Pre-release Version](https://github.com/telegram-sms/telegram-sms-nightly)

**NO WARRANTY EXPRESSED OR IMPLIED. USE AT YOUR OWN RISK!**

**[Telegram SMS compat](https://github.com/telegram-sms/telegram-sms-compat)** - For older Android devices (Android 5.0 or lower). [![Github Release](https://img.shields.io/github/downloads/telegram-sms/telegram-sms-compat/latest/app-release.apk?style=flat-square)](https://github.com/telegram-sms/telegram-sms-compat/releases/latest)


## Features

- Forward SMS text messages to Telegram as a bot;
- Notification regarding missed or incoming calls;
- Notification regarding device battery power changes;
- Carbon Copy - a new way to configure the forward destination(e.g. bark, pushdeer, gotify, etc.).
- Remote control via chat command or SMS.
- Set self-hosted bot API address(See [instructions](./docs/self_hosted_bot_api.md)).

## Permission

This app requires following permissions to work properly:

- SMS: To read and send text messages.
- Phone: Check whether it is a dual SIM-Card phone, the SIM status and its identification digits.
- Call phone: Execute the USSD code.
- Call log: Read incoming numbers.
- Camera: Scan the QR code and quickly enter the Bot Token.
- Notification access: Listen for notification messages.

You can set this APP as the default SMS APP, which will stop popping up SMS notifications and set all received SMS as "read" on the phone.

## User Manual

- [English](https://telegram-sms.com/user-manual.html)
- [简体中文](https://telegram-sms.com/zh_cn/user-manual.html)
- [繁體中文](https://telegram-sms.com/zh_tw/user-manual.html)
- [日本語](https://telegram-sms.com/ja_jp/user-manual.html)
- [Español](https://telegram-sms.com/es_es/user-manual.html)
- [Русский](https://telegram-sms.com/ru_ru/user-manual.html)

## Licenses

Telegram-SMS is licensed under [BSD 3-Clause License](https://get.telegram-sms.com/license).

[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2Fqwe7002%2Ftelegram-sms.svg?type=large)](https://app.fossa.io/projects/git%2Bgithub.com%2Fqwe7002%2Ftelegram-sms?ref=badge_large)

CodeauxLib is licensed under [BSD 3-Clause License](https://github.com/telegram-sms/telegram-sms/blob/master/codeauxlib-license/LICENSE).

Artwork Use free fonts licensed by the whole society: [Refrigerator Deluxe](https://fonts.adobe.com/fonts/refrigerator-deluxe) [站酷庆科黄油体](https://www.zcool.com.cn/work/ZMTg5MDEyMDQ=.html)

Copyright of the artwork belongs to [@walliant](https://www.pixiv.net/member.php?id=5600144). Licensed under [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/).

Cover Author: [@YJBeetle](https://github.com/yjbeetle)

Download resource file of the artwork: [mega.nz](https://mega.nz/#F!TmwQSYjD!XN-uVfciajwy3okjIdpCAQ)

Character set：

- Name: Fay (菲, フェイ)
- Type: Gynoid
- Birthday: 1st October, 2018
- Place of birth: Fujian, China
- Zodiac sign: Libra
- Habits: Eat sweets, Maid dress, Listen Heavy metal music

## Acknowledgements

This APP uses the following open source libraries:

- [okhttp](https://github.com/square/okhttp)
- [Gson](https://github.com/google/gson)
- [CodeauxLib](https://github.com/telegram-sms/CodeauxLibPortable)
- [Paper](https://github.com/pilgr/Paper)
- [AwesomeQRCode](https://github.com/SumiMakito/AwesomeQRCode)
- [code-scanner](https://github.com/yuriy-budiyev/code-scanner)
- [MMKV](https://github.com/Tencent/MMKV)

The creation of this APP would not be possible without the help from the following people:

- [@SumiMakito](https://github.com/SumiMakito) ([Donate](https://paypal.me/makito))
- [@zsxsoft](https://github.com/zsxsoft)
- [@walliant](https://www.pixiv.net/member.php?id=5600144) ([weibo](https://www.weibo.com/p/1005053186671274))

I would also like to thank the following people for their hard work to localise this project:

- English
    - [@tangbao](https://github.com/tangbao)
    - [@jixunmoe](https://github.com/jixunmoe) ([Donate](https://paypal.me/jixun))
- Japanese
    - [@Lollycc](https://github.com/lollycc)
    - [@AisakaMk2](https://github.com/AisakaMk2)
    - [@Qiaogun](https://github.com/Qiaogun)
- Spanish
    - [@David Alonso](https://github.com/lpdavidgc)
- Chinese(Traditional)
    - [@lm902](https://github.com/lm902)
    - [@孟武尼德霍格龍](https://github.com/tony8077616)
- Cantonese (Simp./Trad.) (Remember: these translations are only available when the locale is set/fallbacked to 粵語（香港）or 粤语（中华人民共和国） )
    - [@ous50](https://github.com/ous50)

This APP uses the following public DNS service:

- [1.1.1.1](https://1.1.1.1/)

And finally, [sm.ms](https://sm.ms) for hosting images used in this page.

## Buy me a cup of coffee to help maintain this project further?

- [via Github](https://get.telegram-sms.com/donate/github)

Your donation will make me work better for this project.

## Contributors
<a href="https://github.com/telegram-sms/telegram-sms/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=telegram-sms/telegram-sms" alt="Contributors" />
</a>

Made with [contrib.rocks](https://contrib.rocks).
