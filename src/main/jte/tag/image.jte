@import se.teaterihuskvarna.content.Image
@param Image image
@param String kind = "picture"
@if(image != null)
    <img class="${kind}" src="${image.url()}" alt="${image.alt()}" width="${image.width()}" height="${image.height()}" loading="lazy">
@endif
