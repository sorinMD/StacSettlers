/********************************************************************
 * Implementation of a server DRL agent with the following features:
 * + It communicates with a Java-based WebServer  
 * + It learns non-linear policies via ConvNetJS
 * + It saves and loads policies for testing
 * + It supports full and constrained action spaces
 * + It generates information for plotting learning curves
 * + N.B. this script should be used only for deployment purposes
 * <ahref="mailto:h.cuayahuitl@gmail.com">Heriberto Cuayahuitl</a>
 *******************************************************************/

var WebSocket = require('../node_modules/ws/lib/WebSocket');
var DeepQLearn = require('../convnet/deepqlearn');
var exec = require('child_process').exec, child;
var fs = require('fs');
var net = require('net');

console.log("*TCP Server for STACSettlers*");	
console.log("libraries loaded!");	

var config = {};
require.extensions['.txt'] = function (module, filename) {
	module.exports = fs.readFileSync(filename, 'utf8');
};
var configpipe = require('../../nodeConfig.txt');
parse_configfile(configpipe);

console.log("config file loaded!");

var num_inputs = null; 
var num_actions = null;
var temporal_window = 1; 
var network_size = null;
var brain = null;
var layer_defs = [];
var tdtrainer_options = {learning_rate:0.001, momentum:0.0, batch_size:parseInt(config["BatchSize"]), l2_decay:0.01};

var opt = {};
opt.temporal_window = temporal_window;
opt.experience_size = parseInt(config["ExperienceSize"]);
opt.start_learn_threshold = parseInt(config["BurningSteps"]);
opt.gamma = parseFloat(config["DiscountFactor"]);
opt.learning_steps_total = parseInt(config["LearningSteps"]);
opt.learning_steps_burnin = parseInt(config["BurningSteps"]);
opt.epsilon_min = parseFloat(config["MinimumEpsilon"]);
opt.epsilon_test_time = 0.00;
opt.layer_defs = layer_defs;
opt.tdtrainer_options = tdtrainer_options;

var language = "";//config["Language"];
var output_path = config["OutputPath"];
var training_policy = output_path+"/"+language+"/simpleds-policy";
var training_output = output_path+"/"+language+"/simpleds-output";
var test_policy = output_path+"/"+language+"/simpleds-policy";
var freq_policy_saving = parseInt(config["SavingFrequency"]);

console.log("configurations defined!");

var world, start, action, value;

var World = function() {
	this.agents = [];
	this.data = ["", ""];
	this.clock = 0;
	this.agentID = -1;
	this.totalActions = 0;
	this.totalDialogues = 0;
	this.startTime = new Date().getTime();
}

World.prototype = {      
		tick: function(rawFeatures) {
			this.clock++;
			this.features = [];
			this.observedActions = [];
			this.observedRewards = [];
			
			//console.log("rawFeatures="+rawFeatures);

			var list = rawFeatures.split("|");
			for(var i=0;i<list.length;i++) {
				var pair = list[i].split("=");
				var param = pair[0];

				if (param == "state") {
					var vector = pair[1].split(",");
					for(var j=0;j<vector.length;j++) {
						this.features[j] = parseFloat(vector[j]);
					}

				} else if (param === "actions") {
					var vector = pair[1].split(",");
					for(var j=0;j<vector.length;j++) {
						this.observedActions[j] = parseInt(vector[j]);
						this.totalActions++;
					}

				} else if (param === "rewards") {
					var vector = pair[1].split(",");
					for(var j=0;j<vector.length;j++) {
						this.observedRewards[j] = parseFloat(vector[j]);
					}

				} else if (param === "dialogues") {
					this.totalDialogues = parseFloat(pair[1]);

				} else if (param === "agent") {
					this.agentID = parseInt(pair[1]);

				} else{
					console.log("WARNING: Unknown param="+param);
					console.log("WARNING: rawFeatures="+rawFeatures);
					process.exit(1);
				}
			}
			//console.log("agent="+this.agentID+" s="+this.features + " A="+this.observedActions + " r="+this.observedRewards + " num_nputs="+num_inputs);

			if (this.features.length != num_inputs) {
				console.log("WARNING: The amount of features doesn't match num_inputs="+num_inputs);
				console.log("this.features="+this.features + " size="+this.features.length);
				process.exit(1);
			}

		//console.log("this.agentID="+this.agentID + " this.observedActions="+this.observedActions);

		//if (this.agents[0].brain.learning) {
			this.agents[this.agentID].set_actions(this.observedActions);
			//this.agents[this.agentID].set_actions(this.agents[0].actions);
			this.agents[this.agentID].forward(this.features);
			this.agents[this.agentID].backward(this.observedRewards, this.observedActions);	

		/*} else {
			var A = [];
			var D = {};
			
			for(var i=0;i<this.observedActions.length;i++) {
				var a= this.observedActions[i];
				D[a] = 0;
			}

			this.agents[0].set_actions(this.observedActions);
			this.agents[0].forward(this.features);
			var qvalues0 = this.agents[0].qvalues(this.features);
			var qvalue0 = value;
			A[0] = action;
			D[action] += value;
			//console.log("0: A="+this.observedActions + " a="+action + " Q(a)="+value + " D="+D.toString());

			this.agents[1].set_actions(this.observedActions);
			this.agents[1].forward(this.features);
			var qvalues1 = this.agents[1].qvalues(this.features);
			var qvalue1 = value;
			A[1] = action;
			D[action] += value;
			//console.log("1: A="+this.observedActions + " a="+action + " Q(a)="+value + " D="+D);

			this.agents[2].set_actions(this.observedActions);
			this.agents[2].forward(this.features);
			var qvalues2 = this.agents[2].qvalues(this.features);
			var qvalue2 = value;
			A[2] = action;
			D[action] += value;
			//console.log("2: A="+this.observedActions + " a="+action + " Q(a)="+value + " D="+D);

			this.agents[3].set_actions(this.observedActions);
			this.agents[3].forward(this.features);
			var qvalues3 = this.agents[3].qvalues(this.features);
			var qvalue3 = value;
			A[3] = action;
			D[action] += value;
			//console.log("3: A="+this.observedActions + " a="+action + " Q(a)="+value + " D="+D);
			
			var besta=0;
			var bestv=0;
			for(var i in D) {
				if (D[i]>bestv) {
					besta = i;
					bestv = D[i];
				}
				//console.log("i="+i + " D(i)="+D[i] + " besta="+besta + " bestv="+bestv);
			}
			
			var qvalue01 = (qvalue0>qvalue1) ? qvalue0 : qvalue1; 
			var qvalue23 = (qvalue2>qvalue3) ? qvalue2 : qvalue3; 
			var bestValue = (qvalue01>qvalue23) ? qvalue01 : qvalue23; 
			var bestAgent = -1;
			if (qvalue0 == bestValue) bestAgent = 0;
			else if (qvalue1 == bestValue) bestAgent = 1;
			else if (qvalue2 == bestValue) bestAgent = 2;
			else if (qvalue3 == bestValue) bestAgent = 3;
			else console.log("UNK val...");
			//action = A[bestAgent]; // max from all agents
			//action = besta; // majority value from all agents
			action = A[2]; // decision from best agent
			//console.log("BEST_AGENT="+bestAgent + " a*(max)="+action +" a*(maj)="+besta + "\n");
		}*/
			
			//this.agents[this.agentID].qvalues(this.features);
			/*var qvalues1 = this.agents[1].qvalues(this.features);
			var qvalues2 = this.agents[2].qvalues(this.features);
			var qvalues3 = this.agents[3].qvalues(this.features);
			console.log("Q0="+qvalues0 + " Q1="+qvalues1 + " Q2="+qvalues2 + " Q3="+qvalues3);*/

			/*console.log("a="+action+"s="+this.features + " A="+this.observedActions + " r="+this.observedRewards);
			var qvalues0 = this.agents[0].qvalues(this.features);
			var qvalues1 = this.agents[1].qvalues(this.features);
			console.log("Q0="+qvalues0 + " Q1="+qvalues1);*/
			
			/*if (parseInt(action)>120) ws.send("action="+action);
			//else ws.send(this.agentID + "_trade="+action);
			else ws.send(this.agentID + "_trade="+action);
			//if (this.agentID == 0) ws.send("trade="+action);
			//else ws.send("action="+action);
			//ws.send("action="+action);*/
			return action;
		},
		save: function() {
			if (this.clock%100 === 0 && start === true) {
				/*var average_reward = this.agents[0].brain.average_reward_window.get_average();
				var epsilon = this.agents[0].brain.epsilon;
				var average_actions = this.totalActions / this.clock;
				var currentTime = new Date().getTime();
				var learningTime = (currentTime - world.startTime)/(1000*60*60);
				
				var output_tuple = this.clock +" "+ average_reward.toFixed(4);
				output_tuple += " "+ epsilon.toFixed(4) + " "+ average_actions.toFixed(2);
				output_tuple += " "+ this.totalDialogues +" "+learningTime.toFixed(4);
				world.data[0] += output_tuple + "\n";
				console.log(output_tuple);*/
				console.log("request "+this.clock);
								
				/*var output_tuple0 = this.clock +" 0: "+ average_reward0.toFixed(4);
				output_tuple0 += " "+ epsilon0.toFixed(4) + " "+ average_actions.toFixed(2);
				output_tuple0 += " "+ this.totalDialogues +" "+learningTime.toFixed(4);
				world.data[0] += output_tuple0 + "\n";
				//console.log(output_tuple0);

				var output_tuple1 = this.clock +" 1: "+ average_reward1.toFixed(4);
				output_tuple1 += " "+ epsilon1.toFixed(4) + " "+ average_actions.toFixed(2);
				output_tuple1 += " "+ this.totalDialogues +" "+learningTime.toFixed(4);
				world.data[1] += output_tuple1 + "\n";
				console.log(output_tuple0 + " || " + output_tuple1);*/
			}
			/*if (this.clock%freq_policy_saving === 0 && this.agents[0].brain.learning) {
				save_output(training_output,0);
				//save_output(training_output,1);
				save_policy(training_policy,0);
				//save_policy(training_policy,1);
				//save_policy(training_policy,2);
				//save_policy(training_policy,3);
			}*/
		}
}

var Agent = function() {
	this.brain = brain;
	this.actions = [];
	for(var i=0; i<this.brain.num_actions-1; i++) {
		this.actions.push(""+i);
	}
}

Agent.prototype = {
		forward: function(input_array) {
			this.prevAction = action;
			var tuple = this.brain.forward(input_array);
			action = tuple.maxaction;
			value = tuple.maxvalue;
		},
		backward: function(observedRewards, observedActions) { 			
			if (observedRewards.length == 1) {
				this.brain.backward(observedRewards[0]);
			} else {
				for(var i=0; i<observedActions.length; i++) {
					if (observedActions[i] == action) {
						//console.log("backward: a="+action + " r="+observedRewards[i]);
						this.brain.backward(observedRewards[i]);
						break;
					}
				}
			}
		},
		set_actions: function(array) { 
			this.brain.allowed_actions_array = array;
		},
		qvalues: function(array) { 
			return this.brain.qvalues(array);
		}
}

function save_policy(filename, agentID) {
	var j = world.agents[agentID].brain.value_net.toJSON();
	var text = JSON.stringify(j);
	var file2Save = "../../"+filename+"-"+agentID+".json";
	fs.writeFile(file2Save, JSON.stringify(text, null, 4), function(err) {
		if(err) throw err;
		console.log("Saved policy "+file2Save);
	});
}

function save_output(filename, agentID) {
	var file2Save = "../../"+filename+"-"+agentID+".txt";
	fs.writeFile(file2Save, world.data[agentID], function(err) {
		if(err) throw err;
		console.log("Saved output "+file2Save);
	});
}

function parse_configfile(data) {
    var list = data.split("\n");
    for(var i=0;i<list.length;i++) {
        var line = list[i].trim();
        if (!line || !line.length) {
            continue;
        } else {
            console.log("line="+line);
            var pair = line.split("=");
			console.log("pair="+pair);
            config[pair[0]] = pair[1].toString();
        }
    }
}

function load_policy(filename, agentID) {
	var file2Read = "../../"+filename+"-"+agentID+".json";
	var text = require(file2Read);
	var j = JSON.parse(text);
	world.agents[agentID].brain.value_net.fromJSON(j); 

	console.log("Loading policy "+file2Read);
	console.log("Network initialised!");
}

function run() {
	var execmode = "test";
	if (process.argv.length == 3) {
		//console.log('Usage: node ' + process.argv[1] + ' (train|test)');
		execmode = process.argv[2];
	}

	//var execmode = process.argv[2];
	//ws.send("params="+process.argv);

	if (execmode == "train") {
		world.agents[0].brain.learning = true;
		/*world.agents[1].brain.learning = true;
		world.agents[2].brain.learning = true;
		world.agents[3].brain.learning = true;*/
		console.log("Running in training mode!");
		console.log("<Press ctrl+c to quit>");
		//console.log("[steps avg.reward epsilon avg.actions dialogues time.hrs]");
		start = true;

	} else if (execmode == "test") {
		load_policy(test_policy,0);
		/*load_policy(test_policy,1);
		load_policy(test_policy,2);
		load_policy(test_policy,3);*/
		world.agents[0].brain.learning = false;
		/*world.agents[1].brain.learning = false;
		world.agents[2].brain.learning = false;
		world.agents[3].brain.learning = false;*/
		console.log("Running in test mode!");
		console.log("<Press ctrl+c to quit>");
		//console.log("[steps avg.reward epsilon avg.actions dialogues time.hrs]");
		start = true;
		
		// dummy test
		var features = "";
		for (var i=0; i<num_inputs; i++) {
			features += (features=="") ? "0" : ",0";
		}
		//console.log("features="+features);
		var tuple = "agent=0|state="+features+"|actions=0";
		world.tick(tuple);

	} else {
		console.log("UNKNOWN execution mode!");        
		process.exit(1);
	}
}

function init() {
	start = false;
	world = new World();
	world.agents = [new Agent()];//, new Agent(), new Agent(), new Agent()];
	console.log("Initialised agent!");
}

function createBrain() {
	network_size = num_inputs*temporal_window + num_actions*temporal_window + num_inputs;

	layer_defs.push({type:'input', out_sx:1, out_sy:1, out_depth:network_size});
	layer_defs.push({type:'fc', num_neurons: 50, activation:'relu'});
	layer_defs.push({type:'fc', num_neurons: 50, activation:'relu'});
	layer_defs.push({type:'regression', num_neurons:num_actions});
	opt.layer_defs = layer_defs;

	brain = new DeepQLearn.Brain(num_inputs, num_actions, opt);

	console.log("brain created!");
}

var evt_data = "160,120";
var input_outputs = evt_data.split(",");
num_inputs = parseInt(input_outputs[0]);
num_actions = parseInt(input_outputs[1])+1;
createBrain();
init();
run();

var observedActions = []; 
for(var i=0;i<num_actions;i++) {
	observedActions[i] = parseInt(i); 
} 

var ws = net.createServer(function(socket) {
	socket.on('data', function(evt_data) {
		var decision = world.tick(evt_data.toString());
		socket.write(decision + '\r\n');
		world.save();
	});
});
ws.listen(parseInt(config["TCPServer_Port"]), config["TCPServer_Server"]);
