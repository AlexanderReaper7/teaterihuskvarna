@import java.util.List
@import se.teaterihuskvarna.offer.OfferDetails
@import se.teaterihuskvarna.offer.OfferForm
@import se.teaterihuskvarna.offer.Registrant
@import se.teaterihuskvarna.web.Copy
@import se.teaterihuskvarna.web.CsrfField
@import se.teaterihuskvarna.web.FieldErrors
@param Copy copy
@param CsrfField csrf
@param OfferDetails offer
@param OfferForm form
@param FieldErrors errors
@param List<Registrant> registrations
@param String error
@param String notice
@template.layout(copy = copy, title = offer.title(), content = @`
    <h1>${offer.title()}</h1>
    <p><a href="/admin/erbjudanden">${copy.text("adminOffers.back")}</a></p>
    @if(error != null)
        <p role="alert">${error}</p>
    @endif
    @if(notice != null)
        <p role="status">${notice}</p>
    @endif
    <dl>
        <dt>${copy.text("adminOffers.list.state")}</dt>
        <dd>@if(offer.published())${copy.text("adminOffers.state.published")}@else${copy.text("adminOffers.state.draft")}@endif</dd>
        <dt>${copy.text("adminOffers.closesAt")}</dt>
        <dd>@if(offer.closesAt() == null)${copy.text("adminOffers.closesNever")}@else${copy.dateTime(offer.closesAt())}@endif</dd>
        <dt>${copy.text("adminOffers.list.registered")}</dt>
        <dd>@if(offer.capacity() == null)${offer.registered()}@else${copy.text("adminOffers.list.ofCapacity", offer.registered(), offer.capacity())}@endif</dd>
    </dl>
    <div class="buttons">
        @if(offer.published())
            <form method="post" action="/admin/erbjudanden/${offer.id()}/avpublicera">
                @template.tag.csrf(csrf = csrf)
                <button type="submit" class="button-secondary">${copy.text("adminOffers.unpublish")}</button>
            </form>
        @else
            <form method="post" action="/admin/erbjudanden/${offer.id()}/publicera">
                @template.tag.csrf(csrf = csrf)
                <button type="submit">${copy.text("adminOffers.publish")}</button>
            </form>
        @endif
    </div>

    <h2>${copy.text("adminOffers.registrations.heading")}</h2>
    @if(registrations.isEmpty())
        <p>${copy.text("adminOffers.registrations.none")}</p>
    @else
        <p><a href="/admin/erbjudanden/${offer.id()}/anmalningar.csv">${copy.text("adminOffers.registrations.csv")}</a></p>
        <table>
            <caption>${copy.text("adminOffers.registrations.caption", offer.title())}</caption>
            <thead>
                <tr>
                    <th scope="col">${copy.text("adminOffers.registrations.name")}</th>
                    <th scope="col">${copy.text("adminOffers.registrations.email")}</th>
                    <th scope="col">${copy.text("adminOffers.registrations.phone")}</th>
                    <th scope="col">${copy.text("adminOffers.registrations.at")}</th>
                </tr>
            </thead>
            <tbody>
                @for(Registrant registrant : registrations)
                    <tr>
                        <th scope="row">${registrant.fullName()}</th>
                        <td>@if(registrant.email() != null)${registrant.email()}@endif</td>
                        <td>@if(registrant.phone() != null)${registrant.phone()}@endif</td>
                        <td>${copy.dateTime(registrant.registeredAt())}</td>
                    </tr>
                @endfor
            </tbody>
        </table>
    @endif

    <h2>${copy.text("adminOffers.edit.heading")}</h2>
    @template.tag.offerForm(copy = copy, csrf = csrf, form = form, errors = errors,
            action = "/admin/erbjudanden/" + offer.id(), submit = copy.text("adminOffers.edit.submit"))

    <h2>${copy.text("adminOffers.delete.heading")}</h2>
    <p><a href="/admin/erbjudanden/${offer.id()}/ta-bort">${copy.text("adminOffers.delete.link")}</a></p>
`)
