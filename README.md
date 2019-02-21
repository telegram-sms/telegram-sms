# Telegram SMS

![pipeline status](https://badges.git.reallct.com/qwe7002/telegram-sms/badges/master/pipeline.svg)
![Min Android Version](https://img.shields.io/badge/android-22+-orange.svg)
[![License](https://img.shields.io/badge/License-BSD%203--Clause-blue.svg)](https://github.com/qwe7002/telegram-sms/blob/master/LICENSE)
[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2Fqwe7002%2Ftelegram-sms.svg?type=shield)](https://app.fossa.io/projects/git%2Bgithub.com%2Fqwe7002%2Ftelegram-sms?ref=badge_shield)

A robot running on your Android device.

## Features

- Forward SMS to Telegram
- Monitor missed calls
- Monitor device battery power changes
- Remote control via chat command \ SMS.

## Permission

This app requires the following permissions:

- SMS : Read and send a text message.
- Phone : Get whether it is a dual card phone, card 2 status and identifier ID.
- Call log : Get the incoming number.
- Contact : Get the contact information and automatically identify the incoming caller's number.

You can set this app as the default SMS app, which will block all SMS notifications and automatically set the SMS to read.

## Send SMS

You can specify a trusted phone number for automatic forwarding.Once the bot received a message from that number, in the following format:

```
<Phone_Number>
<SMS_Content>
```
example:
```
729725
balance
```

It will send a text message with content `balance` to the number `729725`.

You can restart all background processes by sending the command `restart-service`

**please add your country/region calling code (eg. +44 for UK phone number) if your are not in default region.**

## Use Chat Command

You can get a list of currently available meters by sending the `/start` command.

The format of sending SMS is:

```
/sendsms
<Phone_Number>
<SMS_Content>
```
example:
```
/sendsms
729725
balance
```
It will send a text message with content `balance` to the number `729725`.

Use `/sendsms` to send a text message via the default SIM card. Use `/sendsms2` to send a text message via the secondary SIM card (if available).

When the Phone permission is not available, you can only use the default SMS SIM card to send SMS.

## License

[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2Fqwe7002%2Ftelegram-sms.svg?type=large)](https://app.fossa.io/projects/git%2Bgithub.com%2Fqwe7002%2Ftelegram-sms?ref=badge_large)

## Acknowledgements

This software uses these open source libraries:

- [okhttp](https://github.com/square/okhttp)

- [Gson](https://github.com/google/gson)

The birth of this software is inseparable from their help:

- [@SumiMakito](https://github.com/SumiMakito)

- [@zsxsoft](https://github.com/zsxsoft)

## Third party user manual

[Android SMS 转发到 Telegram - flinty.moe](https://www.flinty.moe/android-sms-to-telegram/)

## Give a cup of coffee and let me better maintain this project?

- Support me in Payoneer : qwe7002@hotmail.com

- [Support me in Paypal](https://paypal.me/qwe7002)

- [Support me in Cloud QuickPass](https://i.loli.net/2019/02/21/5c6d812840bac.png)(The receipt code is hosted in [sm.ms](https://sm.ms), thanks to the services they provide.)

Your donation will make me work better for this project.