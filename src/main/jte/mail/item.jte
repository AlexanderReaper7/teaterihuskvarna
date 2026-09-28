@import se.teaterihuskvarna.mailing.Mail
@param Mail.Item item
@param String readMore
!{String imageUrl = item.imageUrl();}
!{String place = item.place();}
!{String summary = item.summary();}
!{String bodyHtml = item.bodyHtml();}
<div style="margin:0 0 24px;">
    @if(imageUrl != null)
        <img src="${imageUrl}" alt="${item.imageAlt()}" width="568" style="display:block;width:100%;max-width:568px;height:auto;margin:0 0 8px;">
    @endif
    <h3 style="font-size:18px;margin:0 0 4px;"><a href="${item.url()}" style="color:#1a1a1a;">${item.title()}</a></h3>
    <p style="font-size:14px;margin:0 0 8px;color:#444444;">${item.when()}@if(place != null), ${place}@endif</p>
    @if(summary != null)
        <p style="font-size:16px;line-height:1.5;margin:0 0 8px;">${summary}</p>
    @endif
    @if(bodyHtml != null)
        <div style="font-size:16px;line-height:1.5;">$unsafe{bodyHtml}</div>
    @endif
    <p style="font-size:16px;margin:0;"><a href="${item.url()}" style="color:#1a1a1a;">${readMore}</a></p>
</div>
