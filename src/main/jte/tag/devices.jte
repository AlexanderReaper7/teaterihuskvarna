@import java.util.List
@import se.teaterihuskvarna.login.DeviceDetails
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.DeviceUrls
@param Copy copy
@param CsrfField csrf
@param List<DeviceDetails> devices
@param DeviceUrls urls
<section>
    <h2>${copy.text("device.heading")}</h2>
    <p>${copy.text("device.intro")}</p>
    <table>
        <caption>${copy.text("device.list.caption")}</caption>
        <thead>
            <tr>
                <th scope="col">${copy.text("device.list.name")}</th>
                <th scope="col">${copy.text("device.list.loggedIn")}</th>
                <th scope="col">${copy.text("device.list.lastUsed")}</th>
                <th scope="col">${copy.text("device.list.end")}</th>
            </tr>
        </thead>
        <tbody>
            @for(DeviceDetails device : devices)
                <tr>
                    <td>
                        ${device.name()}
                        @if(device.current())
                            <strong>(${copy.text("device.current")})</strong>
                        @endif
                    </td>
                    <td>${copy.dateTime(device.loggedIn())}</td>
                    <td>${copy.dateTime(device.lastUsed())}</td>
                    <td>
                        <%-- This device logs out as the page's own button does. --%>
                        <form method="post" action="${device.current() ? urls.logout() : urls.end(device.id())}">
                            @template.tag.csrf(csrf = csrf)
                            <button type="submit" class="button-secondary"
                                    aria-label="${copy.text("device.end.label", device.name())}">
                                ${copy.text("device.end")}
                            </button>
                        </form>
                    </td>
                </tr>
            @endfor
        </tbody>
    </table>
    @if(devices.size() > 1)
        <form method="post" action="${urls.endOthers()}">
            @template.tag.csrf(csrf = csrf)
            <button type="submit" class="button-secondary">${copy.text("device.endOthers")}</button>
        </form>
    @endif
</section>
