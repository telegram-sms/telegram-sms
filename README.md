# Telegram SMS
[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2Fqwe7002%2Ftelegram-sms.svg?type=shield)](https://app.fossa.io/projects/git%2Bgithub.com%2Fqwe7002%2Ftelegram-sms?ref=badge_shield)

a tool for forwarding SMS to telegram


Features
========

* Forward SMS to Telegram
* Monitor missed calls
* Monitor device battery power changes

Warning! Setting this program as the default SMS app will not receive mms

Permission
==========

This app requires the following permissions:

- SMS : Read and send a text message.
- Phone : Get whether it is a dual card phone, card 2 status, and card 2 identifier ID.
- Contect : Get the contact information and automatically identify the incoming caller's number.

*Warning! Setting this app as the default SMS app is a very dangerous behavior! This option can only be enabled if SMS notifications are not available in the default SMS application.*

Send SMS
========

You can specify a trusted phone number for automatic forwarding.Once the bot received a message from that number, in the following format:

```
<Phone_Number>
<SMS_Content>
```
example:
```
10086
cxll
```

It will send a text message with content "cxll" to the number "10086".

Use Webhook
===========
We provide a simple http server for webhook on your device's 5000 port, you need to map it to your server using Ngrok or frp, and avoid this http server being accessed directly.

You can register this webhook by visiting the URL.

```
https://api.telegram.org/bot<your_bot_token>/setwebhook?url=https://<your_domain>/webhook_<chat_id>
```


The current bot supports the following instructions, which you can configure in BotFather.

```
getinfo - Get system information
sendsms - Send SMS
sendsms_card2 - Send SMS using the second card slot
```

The format of sending SMS is:

```
/sendsms
<Phone_Number>
<SMS_Content>
```
## License
[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2Fqwe7002%2Ftelegram-sms.svg?type=large)](https://app.fossa.io/projects/git%2Bgithub.com%2Fqwe7002%2Ftelegram-sms?ref=badge_large)
