@import gg.jte.Content
@import se.teaterihuskvarna.content.Image
@param Image image
@param String kind = "picture"
@param String source = null
@param Content content
<%-- A card in a list of cards: its picture, if it has one, in one box and everything else in another.
     site.css gives every card in a row of the list the same two rows, one for each box, so the titles in
     a row start at one height and a row with no picture has no empty box. A card written out by hand in
     a page would lack the boxes.

     The picture has a box of its own because Firefox 155 got the list's height wrong when the img itself
     was the item in the card's grid: /produktioner was 4026 px of list for 2325 px of cards at 2560 px. --%>
<article class="card">
    @if(image != null)
        <div class="card-picture">
            @template.tag.image(image = image, kind = kind, source = source)
        </div>
    @endif
    <div class="card-text">
        ${content}
    </div>
</article>
