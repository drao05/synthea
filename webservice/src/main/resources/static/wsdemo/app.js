$(document).ready(function() {
});

let client;
function connect() {
	client = Stomp.client('ws://localhost:8080/va-synthea/ws');
	
	// Disable Stomp client debug
	client.debug = function(str) {};
	
    client.connect('','', function(data) {
    	$('#connect').attr('disabled', 'disabled');
    	$('#configure').removeAttr('disabled');
    	updateMessage('Connected');
    	
    	// Subscribe to some status channels
    	client.subscribe('/user/reply/start', function(data) {
    		$('#start').attr('disabled', 'disabled');
    		$('#pause').removeAttr('disabled');
    		$('#stop').removeAttr('disabled');
    		updateMessage(data.body);
    	});
    	
    	client.subscribe('/user/reply/pause', function(data) {
    		$('#start').removeAttr('disabled');
    		$('#pause').attr('disabled', 'disabled');
    		$('#stop').attr('disabled', 'disabled');
    		updateMessage(data.body);
    	});
    	
    	client.subscribe('/user/reply/stop', function(data) {
    		$('#configure').removeAttr('disabled');
    		$('#start').attr('disabled', 'disabled');
    		$('#pause').attr('disabled', 'disabled');
    		$('#stop').attr('disabled', 'disabled');
    		updateMessage(data.body);
    	});
    	
    }, function(data) {
    	$('#connect').removeAttr('disabled');
    	$('#configure').attr('disabled', 'disabled');
		$('#start').attr('disabled', 'disabled');
		$('#pause').attr('disabled', 'disabled');
		$('#stop').attr('disabled', 'disabled');
		updateMessage(data.body);
    });
}

let uuid;
let count = 0;
let configureSubscription = null;
let jsonSubscription = null;

function configure() {
	let config = {};
	config['population'] = parseInt($('#population').val());
	if (!configureSubscription) {
		configureSubscription = client.subscribe('/user/reply/configure', function(data) {
			if (jsonSubscription) {
	    		// Unsubscribe from previous result channel if needed.
	    		jsonSubscription.unsubscribe();
	    		jsonSubscription = null;
	    	}
			
	    	let json = JSON.parse(data.body);
	    	if (json['error']) {
	    		updateMessage(data.body);
	    		return;
	    	}
	    	
			$('#configure').attr('disabled', 'disabled');
			$('#start').removeAttr('disabled');
			
	    	uuid = json['uuid'];
	    	updateUUID(uuid);
	    	
	    	count = 0;
	    	updateCountDisplay();
	    	updateMessage(data.body);
	    	
	    	jsonSubscription = client.subscribe('/json/' + uuid, function(data) {
	    		let person = JSON.parse(data.body);
	    		let status = person['status'];
	    		if (status && status === 'Completed') {
	    			$('#configure').removeAttr('disabled');
	        		$('#start').attr('disabled', 'disabled');
	        		$('#pause').attr('disabled', 'disabled');
	        		$('#stop').attr('disabled', 'disabled');
	        		updateMessage(data.body);
	    		} else {
	    			++count;
	    			updateCountDisplay();
	    		}
	        });
		});
	}
	
	client.send('/app/configure', {}, JSON.stringify(config));
}

function start() {
	client.send('/app/start', {}, uuid);
}

function pause() {
	client.send('/app/pause', {}, uuid);
}

function stop() {
	client.send('/app/stop', {}, uuid);
}

function updateUUID(uuid) {
	$('#uuid').html(uuid);
}

function updateCountDisplay() {
	$('#count').html(count);
}

function updateMessage(message) {
	$('#message').html(message);
}
