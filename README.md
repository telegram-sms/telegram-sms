# Telegram SMS

![pipeline status](https://badges.git.reallct.com/qwe7002/telegram-sms/badges/master/pipeline.svg)
![Min Android Version](https://img.shields.io/badge/Min%20Android%20Version-5.0+-orange.svg)
[![License](https://img.shields.io/badge/License-BSD%203--Clause-blue.svg)](https://github.com/telegram-sms/telegram-sms/blob/master/LICENSE)
[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2Fqwe7002%2Ftelegram-sms.svg?type=shield)](https://app.fossa.io/projects/git%2Bgithub.com%2Fqwe7002%2Ftelegram-sms?ref=badge_shield)

A robot running on your Android device.

The primary language used for commit messages is Simplified Chinese. However, you're welcome to use English in commit messages when making contributions.

Please visit [https://reall.uk](https://reall.uk) to submit and discuss issues regarding this project.

You can follow the Telegram channel [Telegram SMS 更新日志 (tg_sms_changelog)](https://t.me/tg_sms_changelog) for the latest news. (Simplified Chinese only)

[Release Download](https://github.com/qwe7002/telegram-sms/releases)

Android 5.0 or lower? [Click here](https://github.com/qwe7002/telegram-sms-compat) to download the latest and greatest.

We have discovered an issue with some improper configurated network such that the connection to `1.1.1.1` via DNS-over-HTTPS (DoH) can fail.
If you could not resolve the IP address for `api.telegram.org` while we are working on a fix, you can turn off DoH temporarily in the settings screen.

**No warranty expressed or implied. Use at your own risk. You have been warned.**

## Features

- Forward SMS text messages to Telegram as a bot;

- Notification regarding missed calls;

- Notification regarding device battery power changes;

- Remote control via chat command or SMS.

## Permission

This app requires following permissions to work properly:

- SMS: To read and send text messages.
- Phone: Check whether it is a dual SIM-Card phone, the SIM status and its identification digits.
- Call log: Read incoming numbers.

You can set this app as the default SMS app, which will prevent SMS notifications and set all received SMS as "read".

## User manual

- [English](https://get-telegram-sms.reall.uk/get/wiki/User_manual)
- [简体中文](https://get-telegram-sms.reall.uk/get/wiki/用户手册)
- [繁體中文](https://get-telegram-sms.reall.uk/get/wiki/用戶手冊)
- [日本語](https://get-telegram-sms.reall.uk/get/wiki/マニュアル)

## License

```
BSD 3-Clause License

Copyright (c) 2018, qwe7002
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

* Neither the name of the copyright holder nor the names of its
  contributors may be used to endorse or promote products derived from
  this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2Fqwe7002%2Ftelegram-sms.svg?type=large)](https://app.fossa.io/projects/git%2Bgithub.com%2Fqwe7002%2Ftelegram-sms?ref=badge_large)

CodeauxLib is licensed under [BSD 3-Clause License](https://github.com/telegram-sms/telegram-sms/blob/master/codeauxlib-license/LICENSE).

Artworks used in Telegram SMS were created by [@walliant](https://www.pixiv.net/member.php?id=5600144).

Copyright belongs to the original author.

Licensed under [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/).

Download resource file: [mega.nz](https://mega.nz/#F!TmwQSYjD!XN-uVfciajwy3okjIdpCAQ)

## Acknowledgements

This APP uses the following open source libraries:

- [okhttp](https://github.com/square/okhttp)
- [Gson](https://github.com/google/gson)
- [CodeauxLib](https://gist.github.com/SumiMakito/59992fd15a865c3b9709b8e2c3bc9cf1)

The creation of this APP would not be possible without help from the following people:

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

This APP uses the following public DNS service:

- [1.1.1.1](https://1.1.1.1/)
- [Quad9](https://www.quad9.net/)
- [dns.sb](https://dns.sb/)

And finally, [sm.ms](https://sm.ms) for hosting images used in this page.

## Buy me a cup of coffee to help maintain this project further?

- [via Paypal](https://paypal.me/nicoranshi)
- [via Bitcoin](bitcoin:17wmCCzy7hSSENnRBfUBMUSi7kdHYePrae) (**17wmCCzy7hSSENnRBfUBMUSi7kdHYePrae**)
- [via Cloud QuickPass](https://static.reallct.com/2019/02/21/5c6d812840bac.png)

Your donation will make me work better for this project.
