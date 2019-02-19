var comms = (function () {

// Keep this variable private inside this closure scope
  let ws;
  let uuid;
  let connected = false;
  let callback = function(data) { updateMessage(data); }
  function updateMessage(message) {
    //$('#message').html(message);
    console.debug(message);
  }
// Expose these functions via an interface while hiding
// the implementation of the module within the function() block

  return {

    setFHIRHandler: function(handler) {
      updateMessage(callback);
      updateMessage(handler);
      callback = handler;
      updateMessage("set callback, allegedly, to handler")
      updateMessage(callback);
    },
    connect: function (url = 'ws://localhost:8080/va-synthea/ws') {
      encounterStats = {};
      try {
        ws = new WebSocket(url);
        ws.onopen = function (event) {
          connected = true;
        };
        ws.onclose = function (event) {
          if (connected) {
            connected = false;
            updateMessage('Connection closed');
          } else {
            updateMessage('Could not connect');
          }
        };
        ws.onmessage = function (event) {
          try {

            let data = JSON.parse(event.data);
            if (data['uuid']) {
              // Got UUID for configured request
              uuid = data['uuid'];
              count = 0;
            }
            if (data['error']) {
              // Got error message
              updateMessage(data['error']);
            } else if (data['status']) {
              // Got status message
              updateMessage(data['status']);
              if (data['status'] === 'Completed') {
                // Request has completed
                count = 0;
              }

              if (data['configuration']) {
                console.log(data['configuration']);
              }

            } else {
              callback(data);
              //processFHIR(data)
              // Got a person
              //$('#count').html(++count);
            }
          } catch (jex) {
            console.error(jex);
          }
        };
      } catch (ex) {
        console.error(ex);
    }
    },

    configure: function (config = {population: 50}) {
      let message = {operation: 'configure', configuration: config};
      ws.send(JSON.stringify(message));
    },

    start: function () {
      updateMessage('Please wait...');
      let message = {operation: 'start', uuid: uuid};
      ws.send(JSON.stringify(message));
    },

    stop: function () {
      updateMessage('Please wait...');
      let message = {operation: 'stop', uuid: uuid};
      ws.send(JSON.stringify(message));
    },

    updateRequest: function (uuid, config) {
      if ((uuid === undefined || uuid === null) || config === null || config === undefined)
        return;
      updateMessage('Please wait...');
      let message = {operation: 'update-request', uuid: uuid, configuration: config};
      ws.send(JSON.stringify(message));
    }

  }
})();
