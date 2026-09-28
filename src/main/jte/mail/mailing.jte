@import se.teaterihuskvarna.mailing.Mail
@param Mail mail
!{String intro = mail.intro();}
<!DOCTYPE html>
<html lang="sv">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${mail.subject()}</title>
</head>
<body style="margin:0;padding:0;background:#ffffff;color:#1a1a1a;font-family:Georgia,serif;">
<div style="max-width:600px;margin:0 auto;padding:24px 16px;">
    <h1 style="font-size:26px;margin:0 0 16px;">${mail.subject()}</h1>
    @if(intro != null)
        @for(String paragraph : intro.split("\\R\\R+"))
            <p style="font-size:16px;line-height:1.5;white-space:pre-line;">${paragraph.strip()}</p>
        @endfor
    @endif
    @if(!mail.events().isEmpty())
        <h2 style="font-size:21px;margin:32px 0 8px;">${mail.labels().events()}</h2>
        @for(Mail.Item item : mail.events())
            @template.mail.item(item = item, readMore = mail.labels().readMore())
        @endfor
    @endif
    @if(!mail.news().isEmpty())
        <h2 style="font-size:21px;margin:32px 0 8px;">${mail.labels().news()}</h2>
        @for(Mail.Item item : mail.news())
            @template.mail.item(item = item, readMore = mail.labels().readMore())
        @endfor
    @endif
    <hr style="border:none;border-top:1px solid #cccccc;margin:32px 0 16px;">
    <p style="font-size:14px;line-height:1.5;color:#444444;">
        <a href="${mail.labels().siteUrl()}" style="color:#1a1a1a;">${mail.labels().site()}</a><br>
        ${mail.labels().why()}<br>
        <a href="{{ unsubscribe }}" style="color:#1a1a1a;">${mail.labels().unsubscribe()}</a>
    </p>
</div>
</body>
</html>
