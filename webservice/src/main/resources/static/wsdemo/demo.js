let ws;
let uuid;
let count = 0;
let connected = false;

function updateMessage(message) {
	$('#message').html(message);
}

function connect() {
	try {
		ws = new WebSocket('ws://localhost:8080/va-synthea/ws');
		ws.onopen = function(event) {
			connected = true;
	    	$('#connect').attr('disabled', 'disabled');
	    	$('#configure').removeAttr('disabled');
	    	updateMessage('Connected');
		};
		
		ws.onclose = function(event) {
			$('#connect').removeAttr('disabled');
	    	$('#configure').attr('disabled', 'disabled');
	    	$('#update-request').attr('disabled', 'disabled');
			$('#start').attr('disabled', 'disabled');
			$('#stop').attr('disabled', 'disabled');
			if (connected) {
				connected = false;
				updateMessage('Connection closed');
			} else {
				updateMessage('Could not connect');
			}
		};
		
		ws.onmessage = function(event) {
			try {
				
				let data = JSON.parse(event.data);
				if (data['uuid']) {
					// Got UUID for configured request
					uuid = data['uuid'];
					$('#uuid').html(uuid);
					
					count = 0;
					$('#count').html(count);
					
					$('#start').removeAttr('disabled');
					$('#stop').attr('disabled', 'disabled');
					$('#update-request').removeAttr('disabled');
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
						$('#configure').removeAttr('disabled');
		        		$('#start').attr('disabled', 'disabled');
		        		$('#stop').attr('disabled', 'disabled');
		        		$('#update-request').attr('disabled', 'disabled');
					}
					
					if (data['configuration']) {
						console.log(data['configuration']);
					}
					
				} else {
					// Got a person
					$('#count').html(++count);
				}
			} catch(jex) {
				console.error(jex);
			}
		};
	} catch(ex) {
		console.error(ex);
	}
}

function configure() {
	updateMessage('Please wait...');
	let message = {operation: 'configure', configuration: {population: parseInt($('#population').val())}};
	ws.send(JSON.stringify(message));
	$('#configure').attr('disabled', 'disabled');
}

function start() {
	updateMessage('Please wait...');
	let message = {operation: 'start', uuid: uuid};
	ws.send(JSON.stringify(message));
	$('#start').attr('disabled', 'disabled');
	$('#stop').removeAttr('disabled');
}

function stop() {
	updateMessage('Please wait...');
	let message = {operation: 'stop', uuid: uuid};
	ws.send(JSON.stringify(message));
	$('#configure').removeAttr('disabled');
	$('#stop').attr('disabled', 'disabled');
}

function updateRequest() {
	updateMessage('Please wait...');
	let message = {operation: 'update-request', uuid: uuid, configuration: { 'telemedAdoptionValues': '1900:0,2000:0,2017:0.9' }};
	ws.send(JSON.stringify(message));
}
