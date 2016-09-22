$(function() {
  function shutter(id) {
    var targets = $("body").find("[shutter='" + id + "']");
    var indicators = $("body").find("[shutter-indicator='" + id + "']");

    console.log("targets = " + targets)
    var target = $(targets[0]);
    console.log("target = " + target)
    console.log("indicators = " + indicators)

    if ( target.attr("shuttered") == "false" ) {
      target.slideUp(200);
      indicators.addClass("spinup").removeClass("spindown");
      target.attr("shuttered", "true");
    } else {
      target.slideDown(200);
      indicators.addClass("spindown").removeClass("spinup");
      target.attr("shuttered", "false");
    }
  }

  var nodes = $("body").find("[shutter-control]");
  nodes.css("cursor", "pointer");
  nodes.click(function() {
    var tgt = $(this).attr("shutter-control");
    shutter(tgt);
  });

  var shuttered = $("body").find("[shuttered='true']");
  shuttered.css("display","none");
});
