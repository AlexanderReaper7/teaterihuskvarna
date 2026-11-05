@import se.teaterihuskvarna.content.Image
@param Image image
@param String kind = "picture"
@param String source = null
@if(image != null)
    <img class="${kind}" data-sanity="${source}" src="${image.url()}" alt="${image.alt()}" width="${image.width()}" height="${image.height()}" loading="lazy">
@endif
