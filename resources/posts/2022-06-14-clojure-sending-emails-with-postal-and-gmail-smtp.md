Title: Clojure: sending emails with postal and Gmail SMTP

While services like [SendGrid](https://sendgrid.com) are easy to use and offer a plethora of features like bounce and open rate monitoring, they are not the cheapest option. Gmail's SMTP service on the other hand, although not as feature rich, is more affordable. In this post we will cover setting up Gmail SMTP with [postal](https://github.com/drewr/postal).

## Limits for sending mail

These are the Gmail per user sending limits:

| Account Type                | messages/rolling 24 hours |
|:----------------------------|--------------------------:|
| Standard Gmail              |                       500 |
| Google Workspace            |                      2000 |
| Google Workspace SMTP relay |                     10000 |

## Cost

At the time of writing SendGrid's price point for 200 000 emails a month is $89.95/mo. Comparatively, to send 10000 per day with Gmail is free if you are already using google workspace, and chances are you do for your company email address. Even if you don't a single seat account is just under 5$ a month and comes with a host of other utilities.

## Setting up Gmail

First we need to make sure our Gmail account has 2-step verification set up. Then we need to create an app password. To do this go to Google account settings and navigate to the "Security" tab. Under "Signing in to Google", click on the "App passwords" menu item (if it's not there you can [access it through this link](https://myaccount.google.com/apppasswords)). Select Other under the "Select app" dropdown and enter the name of the app. Click "Generate" and the new app password will appear on the screen. Copy the password somewhere as we will be needing it later.

## Setting up Google Workspace SMPT (Optional)

[Follow these steps](https://support.google.com/a/answer/2956491) if you want to set up Google Workspace Relay. This is only necessary if you plan on sending more than 2000 messages/rolling 24 hours.

## Postal

[postal](https://github.com/drewr/postal) is a library for constructing and sending RFC822-compliant Internet email messages via SMTP.

To send email with postal we need to call the `send-message` function. The `:host` is set to `"smtp.gmail.com"` (if you are using workspace SMTP relay set it to `"smtp-relay.gmail.com"`), `:port` to `587`, `:tls` to `true`, `:user` to your Gmail address and `pass` to the app password created in the previous step.

```clojure
(require '[postal.core :refer [send-message]])

(send-message
 {:host "smtp.gmail.com"
  :user "your@gmail.com"
  :pass "your-gmail-app-password"
  :port 587
  :tls  true}
 {:from    "foo@gmail.com"
  :to      ["bar@mail.com"
            "baz@mail.com"]
  :subject "You got mail!"
  :body    "Hello!"})
```

For security best practice you will want to pass the values of  `:user` and `:pass` through environment variables rather than hard code them:

```clojure
(send-message
 {:host "smtp.gmail.com"
  :user (System/getenv "EMAIL_USER")
  :pass (System/getenv "EMAIL_PASS")
  :port 587
  :tls  true}
 {;; Gets re-written by google to user email
  :from    "foo@gmail.com"
  :to      ["bar@mail.com"
            "baz@mail.com"]
  :subject "You got mail!"
  :body    "Hello!"})
```

You can now send email via Gmail from your Clojure backend.

*Note 1:  One of the downsides with Gmail SMTP is it doesn't let you overwrite the from field. Meaning the message will always show as being delivered from the email address that send the message. This might make it unsuitable for your use case, so bear that in mind.*

*Note 2: [SendGrid](https://sendgrid.com) is still a fantastic service, and I would still recommend it if you need more advanced monitoring and other advanced features.*

*Note 3: Since [Amazon SES](https://aws.amazon.com/ses/) uses SMTP you can also use postal to send emails through SES instead of Gmail.*
