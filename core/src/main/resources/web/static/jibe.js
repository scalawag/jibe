var Jibe = new function() {
  var Jibe = this;

  var runListPollInterval = 5000;
  var activeMandatePollInterval = 1000;
  var fadeTime = 500;
  var slideTime = 200;

  var reAmp   = new RegExp('&', 'g');
  var reQuot  = new RegExp('"', 'g');
  var reApos  = new RegExp("'", 'g');
  var reLt    = new RegExp('<', 'g');
  var reGt    = new RegExp('>', 'g');
  var reSlash = new RegExp('/', 'g');

  function htmlEscape(str) {
    return str
      .replace(reAmp, '&amp;')
      .replace(reQuot, '&quot;')
      .replace(reApos, '&#39;')
      .replace(reLt, '&lt;')
      .replace(reGt, '&gt;')
      .replace(reSlash, '&#x2F;');
  }

  function formatDate(m) {
    return m.format('MM/DD/YYYY hh:mm:ss a');
  };

  //====================================================================================================================
  // A Watchable is just a holder for a value that provides change events through Backbone's events.

  var Watchable = function(initialValue) {
    this.get = initialValue;
  };

  Watchable.prototype = _.clone(Backbone.Events);

  Watchable.prototype.set = function(newValue) {
    var oldValue = this.get;
    this.get = newValue;
    this.trigger('change', newValue, oldValue);
  };

  //====================================================================================================================
  // This is our model definition.  It contains a bunch of things that are traditionally considered the model as well
  // as a bunch of things that are really the state of the view (like selections and collapsed states).

  var model = new function() {
    var model = this;

    //------------------------------------------------------------------------------------------------------------------

    var Run = Backbone.Model.extend({});

    //------------------------------------------------------------------------------------------------------------------

    var RunList = Backbone.Collection.extend({
      model: Run,
      url: '/data/runs',
    });

    this.runs = new RunList();

    //------------------------------------------------------------------------------------------------------------------

    // Tracks which run selector is selected.  This includes the 'latest' selector.
    this.runSelector = new Watchable(undefined);

    //------------------------------------------------------------------------------------------------------------------

    // Tracks which run is selected (either directly or by virtue of it being the newest and 'latest' is selected).
    this.selectedRun = new Watchable(undefined);

    //------------------------------------------------------------------------------------------------------------------

    this.rootMandate = new Watchable(undefined);

    //------------------------------------------------------------------------------------------------------------------

    this.Mandate = Backbone.Model.extend({
      url: function() {
        return `/data/run/${model.selectedRun.get.id}/${this.id}/status`
      },

      initialize: function() {
        this.updateWhileActive();
      },

      updateWhileActive: function() {
        var self = this;
        var status = self.attributes;

        function isActive() {
          if ( status.composite ) {
            var pendingDescendants = status.leafStatusCounts.PENDING || 0
            var runningDescendants = status.leafStatusCounts.RUNNING || 0
            return ( pendingDescendants + runningDescendants ) > 0;
          } else {
            return ( status.executiveStatus == 'PENDING' || status.executiveStatus == 'RUNNING' );
          }
        };

        if ( isActive() )
          self.fetch().done(function() {
            setTimeout(function() { self.updateWhileActive() }, activeMandatePollInterval);
          });
      },

      children: function() {
        if ( this._children == undefined ) {
          this._children = new MandateChildren([], { parentMandateId: this.id });
        }
        return this._children;
      }
    });

    //------------------------------------------------------------------------------------------------------------------

    var MandateChildren = Backbone.Collection.extend({
      model: model.Mandate,
      initialize: function(models, options) {
        this.parentMandateId = options.parentMandateId;
      },
      url: function() { return `/data/run/${model.selectedRun.get.id}/${this.parentMandateId}/children` },
    });

    //------------------------------------------------------------------------------------------------------------------

    var MandateStatusFilter = function() {
      this.statuses = {
        pending:  true,
        running:  true,
        success:  true, // also for needed
        unneeded: true,
        failure:  true, // also for blocked
      };
    };

    MandateStatusFilter.prototype = _.clone(Backbone.Events);

    MandateStatusFilter.prototype.show = function(status) {
      var sl = status.toLowerCase();
      var key;
      if ( sl == 'needed' )
        key = 'success';
      else if ( sl == 'blocked' )
        key = 'failure';
      else
        key = sl;

      return this.statuses[key];
    }

    MandateStatusFilter.prototype.toggle = function(status) {
      var old = _.clone(this.statuses);
      this.statuses[status] = ! this.statuses[status];
      this.trigger('change', this.statuses, old);
    };

    this.mandateStatusFilter = new MandateStatusFilter();
  };

  this.model = model; // Expose to DOM

  //====================================================================================================================
  // Maintain the run list.

  function getRunView(id) {
    return $('tr#' + id);
  }

  function addRun(runModel) {
    var run = runModel.attributes;

    // Determine where to add the new element based on a reverse-chronological sorting of runs.  The id doubles as
    // the sort key here.

    var trs = $('div#run-list tr.entry');
    var index = _.findLastIndex(trs, function(x) {
      return x.id == 'latest' || x.id > run.id;
    });
    var insertAfterElement = trs.get(index);

    // Create the new element from our template.
    // TODO: update the relative timestamps periodically
    var m = moment(run.timestamp);
    var e = $(`
      <tr id="${run.id}" class="entry ${run.mandate.executiveStatus}">
        <td class="selection"></td>
        <td class="hover"></td>
        <td class="icon"><i class="fa ${Jibe.getIconClassForStatus(run.mandate.executiveStatus)}"></i></td>
        <td class="text">
          <div class="relative">${m.fromNow()}</div>
          <div class="timestamp">${formatDate(m)}</div>
        </td>
      </tr>
    `);

    e.click(function() { model.runSelector.set(run.id) });

    e.insertAfter(insertAfterElement);
    e.fadeIn(fadeTime); // TODO: maybe slide in for better context

    // See if this affected the selected run.  The only way it can is if the 'latest' selector is selected and this is
    // a more recent run than we've yet seen.

    if ( model.runSelector.get == 'latest' && insertAfterElement.id == 'latest' )
      model.selectedRun.set(runModel);
  }

  function changeRun(runModel) {
    var run = runModel.attributes;

    // Determine where to add the new element based on a reverse-chronological sorting of runs.  The id doubles as
    // the sort key here.

    var trs = $('div#run-list tr.entry');
    var index = _.findLastIndex(trs, function(x) {
      return x.id == 'latest' || x.id > run.id;
    });
    var insertAfterElement = trs.get(index);

    // Create the new element from our template.
    // TODO: update the relative timestamps periodically
    var m = moment(run.timestamp);
    var e = $(`
      <tr id="${run.id}" class="entry ${run.mandate.executiveStatus}">
        <td class="selection"></td>
        <td class="hover"></td>
        <td class="icon"><i class="fa ${Jibe.getIconClassForStatus(run.mandate.executiveStatus)}"></i></td>
        <td class="text">
          <div class="relative">${m.fromNow()}</div>
          <div class="timestamp">${formatDate(m)}</div>
        </td>
      </tr>
    `);

    e.click(function() { model.runSelector.set(run.id) });

    $('tr#' + run.id).replaceWith(e);
    e.fadeIn(fadeTime); // TODO: maybe slide in for better context
  }

  function removeRun(runModel) {
    var run = runModel.attributes;

    // This could affect the run selection if the selected run is being removed.

    if ( model.selectedRun.get.id == run.id ) {

      if ( model.runSelector.get == 'latest' ) {

        // It was selected by virtue of it's age and 'latest' being selected.  That means we don't need to change
        // the selector, but we need to change the run that's selected to the new first (most recent) run or clear it
        // if there are no runs.

        if ( model.runs.length > 0 )
          model.selectedRun.set(model.runs.first());
        else
          model.selectedRun.set(undefined);

      } else {

        // The removed run was explicitly selected.  Selection should move to the next most recent run. If there are
        // no more recent runs, just select 'latest.'

        var nextRunIndex = model.runs.findLastIndex(function(x) { return x.attributes.timestamp > run.timestamp });
        if ( nextRunIndex < 0 )
          model.runSelector.set('latest');
        else
          model.runSelector.set(model.runs.at(index).id);

      }
    }

    // Fade out and destroy the run list entry.

    var tr = getRunView(run.id);
    tr.fadeOut(fadeTime, function() { tr.remove() });
  }

  //====================================================================================================================
  // Keeps track of the mandates for the selected run.  The tree is reused as the user navigates among different runs.
  // The idea is that the runs more than likely will contain the same mandates.  This will preserve as much of the
  // mandate view (collapsed/expanded state and location) as possible.

  var rootMandate;

  //====================================================================================================================
  // The MandateView class is used to render one mandate job summary, which maps to a div with a summary and
  // collapsible details in the DOM.

  var MandateView = function(model) {
    this.collapsed = true;
    this.children; // undefined means unknown, empty means no children.
    this.log;      // undefined means unknown, empty means no children.

    // Create the constant elements (the ones that don't change when the status changes).

    this.elt = $(`
      <div id="${model.id}" class="mandate">
        <div class="summary-holder">
          <!-- Used to push the details down below the stacked summaries. Remains hidden. -->
          <div class="summary-spacer">
            <div class="description">
              <span class="force-content"></span>
            </div>
          </div>
        </div>
        <div class="details"></div>
      </div>
    `);

    this.detailsElt = this.elt.find('.details');

    // If this is a leaf mandate, initialize its log.
    if ( model.attributes.composite ) {
      this.childView = new MandateChildrenView(this.detailsElt, this.model);
    } else {
      // TODO: eventually have the logs load just-in-time when the leaf mandate is expanded instead of all at once?
      this.log = new Log(model.id);
      this.detailsElt.append(this.log.elements.root);
    }

    // The Mandate model that we're listening to and rendering.  This value must be set by setModel() to ensure
    // that the event listening is properly wired.  This should also get the initial state of the model (and the
    // log for our view).

    this.setModel(model);

    // Always listen for filter changes and make sure that we're initialized properly.
    var self = this;
    Jibe.model.mandateStatusFilter.on('change', function() { self.updateFiltering() });
    this.updateFiltering();
  }

  MandateView.prototype.setModel = function(m) {
    var self = this;

    if ( self.model == m )
      return;

    // Stop listening to the old model.
    if ( self.model ) {
      self.model.off('change', null, self);
    }

    self.model = m;

    // Start listening to the new model.
    if ( self.model ) {
      self.model.on('change', function() { self.updateSummaryElement() }, self);

      // Update the summary (the colored mandate bar).
      self.updateSummaryElement();

      // Update child mandates, if we have them (we're a composite).
      if ( self.childView )
        self.childView.setModel(self.model);

      // Update the log if we have one (we're not a composite).
      if ( self.log ) {
        self.log.reset();
        // Piggyback on the model's fetches to update the log.  It will automatically stop when the model reaches
        // a terminal state (which means that no more logging should happen).
        self.model.on('sync', function() { self.log.update() });
        self.log.update();
      }
    }
  }

  MandateView.prototype.updateFiltering = function() {
    // Update the visibility of this mandate based on the filters that are in effect.  If this is a composite and
    // nothing that descends from this mandate is visible, it's invisible.
    var status = this.model.attributes;

    var show;
    if ( status.composite ) {
      // See if any of our leaf statuses are visible and have count > 0.
      show = undefined != _.find(_.pairs(status.leafStatusCounts), function(pair) {
        var leafStatus = pair[0];
        var count = pair[1];
        return Jibe.model.mandateStatusFilter.show(leafStatus) && count > 0;
      });
    } else {
      show = Jibe.model.mandateStatusFilter.show(status.executiveStatus);
    }

    if ( show )
      this.elt.removeClass('filtered');
    else
      this.elt.addClass('filtered');
  }

  MandateView.prototype.updateView = function() {
    this.updateSummaryElement();
  }

  // Create the summary and details structure (which doesn't change throughout the life of this Mandate.  Only the
  // strings and classes will change from now on.  This intentionally does not have access to the status because I
  // want to make sure that the updateSummary() is used to redraw everything when a new status is passed in.

  MandateView.prototype.toggleShutter = function() {
    if ( this.collapsed ) {
      this.detailsElt.slideDown(slideTime);
      this.spinnerElt.rotate({ animateTo: 90, duration: slideTime });
      this.collapsed = false;
    } else {
      this.detailsElt.slideUp(slideTime);
      this.spinnerElt.rotate({ animateTo: 0, duration: slideTime });
      this.collapsed = true;
    }
  }

  MandateView.prototype.updateSummaryElement = function() {
    var self = this;
    var status = self.model.attributes;

    var time = '';
    var timeTitle = '';

    if ( status.startTime ) {
      var startMoment = moment(status.startTime);
      if ( status.endTime ) {
        var endMoment = moment(status.endTime);
        time = ( status.endTime - status.startTime ) + ' ms';
        timeTitle = formatDate(startMoment) + ' - ' + formatDate(endMoment);
      } else {
        time = ( moment() - status.startTime ) + ' ms';
        timeTitle = formatDate(startMoment) + ' - ';
      }
    }

    // Build a new summary element from the template using the current status.
    var newSummaryElement = $(`
      <div class="summary ${status.executiveStatus}">
        <div class="link"><a href="/#/runId/${status.id}"><i class="fa fa-link"></i></a>&nbsp;</div>
        <div class="collapse-control">
          <div class="collapser">
            <i class="fa fa-caret-right"></i>
          </div>
          <div class="icon">
            <i class="fa ${Jibe.getIconClassForStatus(status.executiveStatus)}"></i>
          </div>
          <div class="time" title="${timeTitle}">${time}</div>
          <div class="description">
            <span class="force-content">${htmlEscape(status.description || '')}</span>
          </div>
        </div>
      </div>
    `);

    // Remember the collapser div for spinning later
    this.spinnerElt = newSummaryElement.find('.collapser');
    if ( ! this.collapsed )
      this.spinnerElt.rotate(90);

    // Make the collapse-control element the part that collapses the details.
    newSummaryElement.find('.collapse-control').click(function () { self.toggleShutter() });

    var summaryHolder = self.elt.children('.summary-holder');

    // Find the old summary elements that we're going to delete _prior_ to appending our new summary.

    var oldSummaryElements = summaryHolder.children('.summary');

    // Fade it in over the old summary and then delete the old one.
    summaryHolder.append(newSummaryElement);
    newSummaryElement.fadeOut(0, function() {
      newSummaryElement.fadeIn(fadeTime, function() {
        oldSummaryElements.remove();
      });
    });
  }

  MandateView.prototype.exposeNothing = function() {
    if ( ! this.collapsed )
      this.toggleShutter();
  };

  MandateView.prototype.exposeMandates = function() {
    if ( this.model.attributes.composite ) {
      if ( this.collapsed )
        this.toggleShutter();
    } else {
      if ( ! this.collapsed )
        this.toggleShutter();
    }
  };

  MandateView.prototype.exposeLogs = function() {
    if ( this.collapsed )
      this.toggleShutter();
  };

  MandateView.prototype.foreachDescendantMandateView = function(fn) {
    var self = this;
    fn(self);
    if ( this.childView ) {
      this.childView.foreachDescendantMandateView(fn);
    }
  };

  //====================================================================================================================
  // The MandateChildrenView class is used to render the children of a given mandate into a specific DOM element.

  var MandateChildrenView = function(parentElement, parentMandate) {
    this.parentElement = $(parentElement);
    this.parentMandate = parentMandate;
    this.children = [];

    this.setModel(parentMandate);
  }

  MandateChildrenView.prototype.setModel = function(parentMandate) {
    var self = this;

//TODO: no listening needed, just one fetch (the number of mandates don't change)
//    // Stop listening to the old model.
//    if ( self.model ) {
//      self.model.off('reset', null, self);
//    }

    self.parentMandate = parentMandate;

    // Start listening to the new model.
    if ( self.parentMandate ) {
//      self.model.on('reset', function() { self.updateView() }, self);
      // Trigger a fetch and reset, which will also trigger the listener above.
      self.parentMandate.children().fetch({
        reset: true,
        success: function() {
          self.updateView();
        },
      });
    } else {
      self.updateView(); // should clear out the view.
    }
  }

  MandateChildrenView.prototype.updateView = function() {
    var self = this;

    var models = ( self.parentMandate && self.parentMandate.children().models ) || [];

    var pairs = _.zip(models, self.children);

    $.each(pairs, function(n, pair) {
      var childModel = pair[0];
      var childView = pair[1];

      if ( childModel && childView ) {
        // Update the existing child view to point to the new child model.
        childView.setModel(childModel);
      } else if ( childModel ) {
        // Create a new child view for this child model
        var v = new MandateView(childModel);
        // Remember it for next time.
        self.children.push(v);
        // Add the view to the DOM
        var e = v.elt;
        e.css('display', 'none');
        e.css('opacity', '0');
        self.parentElement.append(e);
        e.slideDown(slideTime, function() {
          e.fadeTo(fadeTime, 1);
        });
      } else {
        // We've got too many child views, remove this one.
        var e = childView.elt;
        e.fadeTo(fadeTime, 0, function() {
          e.slideUp(slideTime, function() {
            e.remove();
          });
        });
        // Remove from our internal list.
        self.children.splice(n, 1);
      }
    });
  }

  MandateChildrenView.prototype.foreachDescendantMandateView = function(fn) {
    var self = this;
    if ( this.children ) {
      _.each(this.children, function(c) {
        c.foreachDescendantMandateView(fn);
      });
    }
  };

  //====================================================================================================================
  // Manages collapsible exception stack traces in the mandate logs.

  var ExceptionStackTrace = function(id) {
    this.id = id;
    this.hasStackFrames = false;
    this.collapsed = true;

    // Create the DOM structure for a stack trace

    var elt = $(`
      <div id="${id}" class="stack-trace">
        <div class="collapser"><i class="fa fa-caret-right"></i></div>
        <div class="collapser-insert">
          <div class="message"/>
          <div class="location"/>
        </div>
      </div>
    `);

    this.elements = {
      root: elt,
      messages: $(elt.find('div.message')),
      frames: $(elt.find('div.location')),
      spinner: $(elt.find('div.collapser')),
    };

    var self = this;
    this.elements.messages.click( function() { self.toggleShutter() }).css('cursor', 'pointer');
    this.elements.spinner.click( function() { self.toggleShutter() }).css('cursor', 'pointer');
  }

  ExceptionStackTrace.prototype.toggleShutter = function() {
    if ( this.collapsed ) {
      this.elements.frames.slideDown(slideTime);
      this.elements.spinner.rotate({ animateTo: 90, duration: slideTime });
      this.collapsed = false;
    } else {
      this.elements.frames.slideUp(slideTime);
      this.elements.spinner.rotate({ animateTo: 0, duration: slideTime });
      this.collapsed = true;
    }
  }

  ExceptionStackTrace.prototype.classify = function(line) {
    if ( line.text.startsWith('\tat ') || line.text.startsWith('\t...') ) {
      line.classification = 'stack-frame';
    } else if ( line.text.startsWith('Caused by: ') ) {
      // This could eventually be another classification 'cause' if we want to nest them within the primary.
      line.classification = 'message';
    } else {
      line.classification = 'message';
    }
    return line;
  }

  ExceptionStackTrace.prototype.createLineDiv = function(line) {
    return $(`<div class="${line.classification}  line ${line.level}" title=${line.timestamp}>${line.text}</div>`);
  }

  // Returns true if the message was appended and false if it could not be (usually because it's a 'message' type
  // message and this object already contains stack frames).

  ExceptionStackTrace.prototype.append = function(line) {
    line = this.classify(line)
    switch (line.classification) {
      case 'stack-frame':
        if (! this.hasStackFrames) {
          this.hasStackFrames = true;
        }
        this.elements.frames.append(this.createLineDiv(line))
        return true;

      case 'message':
        if (this.hasStackFrames) {
          return false;
        } else {
          this.elements.messages.append(this.createLineDiv(line))
          return true;
        }
    }
  }

  //====================================================================================================================
  // Manages collapsible method calls in the mandate logs.

  var MethodCall = function() {
  }

  //====================================================================================================================

  var CommandExecution = function(id) {
    this.id = id;
    this.collapsed = true;

    this.elements = {
      root: commandDiv = $(`<div id="${id}" class="command"/>`)
    };
  }

  CommandExecution.prototype.toggleShutter = function() {
    if ( this.collapsed ) {
      this.elements.scriptContent.slideDown(slideTime);
      this.elements.spinner.rotate({ animateTo: 90, duration: slideTime });
      this.collapsed = false;
    } else {
      this.elements.scriptContent.slideUp(slideTime);
      this.elements.spinner.rotate({ animateTo: 0, duration: slideTime });
      this.collapsed = true;
    }
  }

  CommandExecution.prototype.appendHeader = function(line) {
    var self = this;

    var elt = $(`
      <div class="header">
        <div class="collapser"><i class="fa fa-caret-right"></i></div>
        <div class="collapser-insert">
          <div class="section start">
            <div class="line">Command: ${line.text}</div>
          </div>
          <div class="section content">
            <div class="line-numbers"></div>
          </div>
        </div>
      </div>
    `);

    this.elements.header = $(elt.find('.start'));
    this.elements.sections = $(elt.find('.collapser-insert'));
    this.elements.scriptContent = $(elt.find('.content'));
    this.elements.lineNumbers = $(elt.find('.line-numbers'));
    this.elements.spinner = $(elt.find('.collapser'));
    this.currentLineNumber = 1;

    var self = this;
    this.elements.header.click(function() { self.toggleShutter() }).css('cursor', 'pointer');
    this.elements.spinner.click(function() { self.toggleShutter() }).css('cursor', 'pointer');

    this.elements.root.append(elt);
  }

  CommandExecution.prototype.appendContent = function(line) {
    var ndiv = $(`
      <div class="line-number">${this.currentLineNumber}:</div>
    `);

    var ldiv = $(`
      <div class="line" title="${line.timestamp}'"><span class="force-content">${line.text}</span></div>
    `);

    this.elements.lineNumbers.append(ndiv);
    this.elements.scriptContent.append(ldiv);
    hljs.highlightBlock(ldiv[0]);

    this.currentLineNumber += 1;
  }

  CommandExecution.prototype.appendOutput = function(line) {

    // Create the output section if it's not already there
    if ( this.elements.scriptOutput == undefined ) {
      this.elements.scriptOutput = $('<div class="section output"/>');
      this.elements.sections.append(this.elements.scriptOutput);
    }

    var elt = $(`
      <div class="line ${line.level}" title="${line.timestamp}"><span class="force-content line">${line.text}</span></div>
    `);

    this.elements.scriptOutput.append(elt);
  }

  CommandExecution.prototype.appendFooter = function(line) {

    // Create the exit section if it's not already there
    if ( this.elements.exitSection == undefined ) {
      this.elements.exitSection = $('<div class="section exit"/>');
      this.elements.sections.append(this.elements.exitSection);
    }

    var elt = $(`
      <div class="line ${line.level}" title="${line.timestamp}"><span class="force-content line">Exit Code = ${line.text}</span></div>
    `);

    elt.click(function() { self.toggleShutter() }).css('cursor', 'pointer');

    this.elements.exitSection.append(elt);
  }

  CommandExecution.prototype.append = function(line) {
    switch (line.tag) {
      case 'CS': this.appendHeader(line);  break;
      case 'CC': this.appendContent(line); break;
      case 'CO': this.appendOutput(line);  break;
      case 'CE': this.appendFooter(line);  break;
    }
  }

  //====================================================================================================================
  // Log class is used to maintain the log for a single leaf mandate.  Its main goal is to make it so that it can
  // build up the log display one line at a time. This makes it possible to append to the log without having to
  // reprocess the whole thing from scratch whenever new lines are written.

  var Log = function(mandateId) {
    this.mandateId = mandateId;
    this.bytesRendered = 0;
    this.lastStackTraceSerial = -1;
    this.lastCommandSerial = -1;

    this.parseLine = function(line) {
      var parts = line.split('|');

      // Handle |s in the actual text of the log line by replacing them.
      if ( parts.length > 4 ) {
        parts[3] = parts.slice(3).join('|');
      }

      return {
        tag: parts[0],
        level: parts[1],
        timestamp: parts[2],
        text: parts[3]
      }
    }

    var elt = $(`
      <div class="row mono">
        <div id="${mandateId}_L" class="log"/>
      </div>
    `);

    this.lastItem = undefined;

    this.elements = {
      root: elt,
      lines: $(elt.find('.log')),
    }
  };

  Log.prototype.reset = function() {
    this.elements.lines.empty();
    this.bytesRendered = 0;
  };

  Log.prototype.appendLine = function(rawLine) {
    var line = this.parseLine(rawLine);

    switch (line.tag) {

      case 'EE':

        // Exception line.  We need to create a new ExceptionStackTrace if we can't append it to (possible) one that's
        // already at the end of the log.

        if ( this.lastItem == undefined || ! this.lastItem instanceof ExceptionStackTrace || ! this.lastItem.append(line) ) {
          // The lastItem (if it's there) can't take this line.  Add new EST and append this line to it.

          this.lastStackTraceSerial += 1;
          this.lastItem = new ExceptionStackTrace(this.mandateId + '_L_E_' + this.lastStackTraceSerial);
          this.elements.lines.append(this.lastItem.elements.root);
          this.lastItem.append(line);
        }

        break;

      case 'CS':
        this.lastCommandSerial += 1;
        this.lastItem = new CommandExecution(this.mandateId + '_L_C_' + this.lastCommandSerial);
        this.elements.lines.append(this.lastItem.elements.root);

        // fall through and append this line.

      case 'CC':
      case 'CO':
      case 'CE':
        // We better have seen the CS that makes this.lastItem a CommandExecution that can handle these.
        this.lastItem.append(line);
        break;


  //    case 'FS':
  //      <div class="call">
  //        <div class="collapser" shutter-control={sid} shutter-indicator={sid}><span class="fa fa-caret-right"></span></div>
  //        <div class="collapser-insert">
  //          <div class="section start">
  //            <div class="line" shutter-control={sid}>Command: {cmd.name.text}</div> <!-- TODO: timestamp -->
  //          </div>
  //          <div class="section content" shutter={sid} shuttered="true">
  //
  //      var e =
  //        $('<div>', {
  //          class: level + ' line ' + tag,
  //          title: timestamp,
  //        }).text('Mandate Call: ' + text);
  //
  //      this.appendElement(e);
  //      break;
  //
  //    case 'FR':
  //
  //      var e =
  //        $('<div>', {
  //          class: level + ' line ' + tag,
  //          title: timestamp,
  //        }).text('Returned: ' + text);
  //
  //      this.appendElement(e);
  //      break;

      default:
        var e =
          $('<div>', {
            class: line.level + ' line ' + line.tag,
            title: line.timestamp,
          }).text(line.text);

        this.elements.lines.append(e);
    }

    this.bytesRendered += rawLine.length + 1;
  }

  Log.prototype.appendText = function(text) {
    var self = this;
    var lines = text.split('\n');

    // Always skip the last line.  It could be partial (and we don't know how to render partial lines) and the
    // last line, once the log is complete, will always be empty.
    lines.pop();

    $.each(lines, function(n, line) {
      self.appendLine(line);
    });
  }

  Log.prototype.update = function() {
    var self = this;
    var bytesRendered = self.bytesRendered; // remember this for the handler

    $.ajax({
      url: `/data/run/${model.selectedRun.get.id}/${self.mandateId}/log/${self.bytesRendered}`,
      type: 'GET',
      dataType: 'text',
      complete: function ( rsp ) {
        // Make sure no other handler has already fetched the same bytes. This is a workaround for the case where
        // two updates are made nearly simultaneously.  TODO: prevent this _before_ the extra request happens!
        if ( rsp.status == 200 ) {
          if ( self.bytesRendered == bytesRendered )
            self.appendText(rsp.responseText);
          else
            console.warn('got badness ' + self.mandateId + ' ' + (self.bytesRendered == bytesRendered));
        }
      },
    });
  }

  //======================================================================================================================

  this.getIconClassForStatus = function(status) {
    switch ( status ) {
      case 'PENDING' : return 'fa-clock-o';
      case 'RUNNING' : return 'fa-gear fa-spin';
      case 'UNNEEDED': return 'fa-times';
      case 'FAILURE' : return 'fa-exclamation';
      case 'SUCCESS' : return 'fa-check';
      case 'NEEDED'  : return 'fa-check';
      case 'BLOCKED' : return 'fa-ban';
    }
  };

  this.clickFilter = function(elt) {
    var control = $(elt);
    var status = control.attr('status');

    Jibe.model.mandateStatusFilter.toggle(status);
  };

  this.exposeNothing = function() {
    if ( this.rootView )
      this.rootView.foreachDescendantMandateView(function(x) {
        x.exposeNothing();
      });
  };

  this.exposeMandates = function() {
    if ( this.rootView )
      this.rootView.foreachDescendantMandateView(function(x) {
        x.exposeMandates();
      });
  };

  this.exposeLogs = function() {
    if ( this.rootView )
      this.rootView.foreachDescendantMandateView(function(x) {
        x.exposeLogs();
      });
  };

  this.selectLatest = function() {
    model.runSelector.set('latest');
  }

  this.initialize = function() {
    model.runs.on('add', addRun);
    model.runs.on('remove', removeRun);
    model.runs.on('change', changeRun);

    // Update the view with a new run selection when it changes.

    model.runSelector.on('change', function(n, o) {
      if ( o ) getRunView(o).removeClass('selected');
      if ( n ) getRunView(n).addClass('selected');
    });

    // Update the filter controls when the filters change.

    model.mandateStatusFilter.on('change', function(filters) {
      $('td#filters i').each( function(n, i) {
        var control = $(i);
        var status = control.attr('status');

        if ( filters[status] )
          control.addClass('toggle-on').removeClass('toggle-off');
        else
          control.addClass('toggle-off').removeClass('toggle-on');
      });
    });

    // TODO: Update document location when runId changes or mandateId changes

    // TODO: rebuild the mandate tree when the runId changes

    model.runSelector.on('change', function(n, o) {

      // Determine the new run, either because the new selection is 'latest' and it's the latest of because the new
      // selection is its ID.

      var run;
      if ( n == 'latest' ) {
        if ( model.runs.length > 0 )
          run = model.runs.first();
      } else if ( n ) {
        run = model.runs.get(n);
      }

      model.selectedRun.set(run);
    });

    model.selectedRun.on('change', function(n) {
      if ( n )
        model.rootMandate.set(new model.Mandate({id: 'm0'}));
      else
        model.rootMandate.set(undefined);
    });

    // Update the run ID at the top of the mandates when the selected run changes.

    model.selectedRun.on('change', function(run) {
      if ( run )
        $('#run-id').text(formatDate(moment(run.attributes.timestamp)));
      else
        $('#run-id').empty;
    });

    // Update the run header to reflect the status of the root mandate when the selected run changes.

    model.rootMandate.on('change', function(n, o) {
      var iconView = $('#run-status i');
      var header = $('#run-header');

      if ( n ) {
        var icon = Jibe.getIconClassForStatus(n.attributes.executiveStatus);
        iconView.attr('class', 'fa ' + icon);
        header.attr('class', 'mandate ' + n.attributes.executiveStatus);
      } else {
        iconView.attr('class', 'fa');
        header.attr('class', 'mandate');
      }
    });

    // (Re)Populate the mandate tree with the children of the root mandate when the root mandate changes.

    var rootView = new MandateChildrenView('div#mandate-tree');
    this.rootView = rootView;

    model.rootMandate.on('change', function(n, o) {
      rootView.setModel(n);
    });

    // Initialize the selection to 'latest'
    model.runSelector.set('latest');

    // Poll for run list changes.  Everything else flows from here.
    model.runs.fetch();
    setInterval(function() { model.runs.fetch() }, runListPollInterval);
  };

};

$(function() {
  Jibe.initialize();
});
