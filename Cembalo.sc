Cembalo {
	// Timbral parameters
	var gTimbre = 0, gPan = 0, <gBodyindex = 0, <gAmp = 0.7, gAttack = 0, gRelease = 0, lagTime = 0.1;
	var <gOut, <outputmapping;

	// System variables
	var server, path, userSamplePath, fillLostSamples, <configurationPath, <configuration;
	var buffersOnServer;
	var <bodySynthdef, <releaseSynthdef, <bodySynthdefMono, <releaseSynthdefMono;

	// Tuning variables
	var <tuning, <scaleRoot = 0, <rootFreq = 440;
	var rates, masterRate, <transposedRates, <tuningType, bufferIndexOffset = 0;
	var <midiNoteOffset = 24, <midiNoteCeil;

	// Buffer variables
	var <buffers;

	// Playing variables
	var <keys;
	var currentChord;
	var keyEventIndex;

	// *** Class method: new
	*new {
		| tuning = \et12
		, scaleRoot = 0
		, rootFreq = 440
		, rootFreqIndex = 9
		, amp = 0.7
		, outputmapping = 0
		, mixToMono = false
		, userSamplePath = nil
		, fillLostSamples = false
		, server = nil
		, onLoad = nil
		|
		
		^super.new.initCembalo(
			tuning,
			scaleRoot,
			rootFreq,
			rootFreqIndex,
			amp,
			outputmapping,
			mixToMono,
			userSamplePath,
			fillLostSamples,
			server,
			onLoad
		);
	}

	// *** Instance method: initCembalo
	initCembalo {
		| tuning
		, scaleRoot
		, rootFreq
		, rootFreqIndex
		, amp
		, outputmapping
		, mixToMono
		, userSamplePath
		, fillLostSamples
		, srv
		, onLoad
		|

		server = srv ? Server.local;
		path = Platform.userExtensionDir ++ "/cembalo/";

		// Initialize keys array
		keys = nil!128;
		keyEventIndex = 0!128;
		currentChord = 0!128;

		// Initialize buffers dictionary
		buffers = Dictionary();

		// Set output mapping
		this.setOutputmapping(outputmapping);

		// Load configuration
		this.loadConfiguration(userSamplePath);

		if(mixToMono, {
			bodySynthdef = \cembalo_player_mix_to_mono;
			releaseSynthdef = \cembalo_player_oneshot_mix_to_mono;
			bodySynthdefMono = \cembalo_player_mono;
			releaseSynthdefMono = \cembalo_player_oneshot_mono;
		}, {
			bodySynthdef = \cembalo_player;
			releaseSynthdef = \cembalo_player_oneshot;
			bodySynthdefMono = \cembalo_player_mono_to_stereo;
			releaseSynthdefMono = \cembalo_player_oneshot_mono_to_stereo;
		});

		server.waitForBoot({
			"Loading Synthdefs...".postln;
			this.loadSynthDefs();
			server.sync;
			"Done.".postln;

			"Checking if buffers are already loaded on server...".postln;
			buffersOnServer = [];
			// With help from David Granström
			server.cachedBuffersDo{|buf|
				buffersOnServer = buffersOnServer.add(buf)
			};
			server.sync;
			"Done.".postln;

			"Loading buffers...".postln;
			this.loadBuffers(userSamplePath);
			server.sync;
			"Done.".postln;

			// "Applying configuration to buffers...".postln;
			// buffers.do{|item|
			// 	var num = item[\nn].asString;

			// 	item[\centOffset] = configuration["keys"][num]["cent"].asFloat;
			// };
			// server.sync;
			// "Done.".postln;

			"Loading keys...".postln;
			this.loadKeys(buffers);
			server.sync;
			"Done.".postln;

			if(fillLostSamples, {
				"Creating additional keys...".postln;
				(0..127).do{|nn|
					if(keys[nn] == nil, {
						var output = outputmapping[nn%outputmapping.size];
						var sampleindex = this.findClosestSample(nn);

						"creating new key with note number % using sample %\n".postf(
							nn, sampleindex);
						
						keys[nn] = CembaloKey(
							nn: nn,
							out: output[0],
							outL: output[0],
							outR: output[1],
							amp: gAmp,
							pan: 0,
							attack: gAttack,
							release: gRelease,
							bodyBuffer: buffers[sampleindex][\body][gBodyindex],
							releaseBuffer: buffers[sampleindex][\release],
							parent: this.value()
						);
					})
				};
				server.sync;
				"Done".postln;
			});

			"Setting tuning...".postln;
			this.setTuning(tuning, scaleRoot);
			server.sync;
			"Done.".postln;

			"Setting root freq...".postln;
			this.setRootFreq(rootFreq, rootFreqIndex);
			server.sync;
			"Done.".postln;

			"Setting up event type...".postln;
			this.eventTypeSetup();
			server.sync;
			"Done.".postln;

			if(onLoad.class == Function, {
				onLoad.value(this)
			});

			server.sync;

			"=== CEMBALO LOADED ===".postln;
		});
	}
	// initCembalo {
	// 	| out
	// 	, iTuning
	// 	, amp
	// 	, iOutputmapping
	// 	, mixToMono
	// 	, iUserSamplePath
	// 	, fillLostSamples
	// 	, onLoad
	// 	|
	// 	"            =============".postln;
	// 	"            == Cembalo ==".postln;
	// 	"            =============".postln;
	// 	gOut = out;
	// 	tuning = iTuning;
	// 	root = 0;
	// 	rootFreq = 440;
	// 	gAmp = amp;
	// 	outputmapping = iOutputmapping;
	// 	userSamplePath = iUserSamplePath;

	// 	if(userSamplePath.notNil, {
	// 		if(userSamplePath[userSamplePath.size - 1] != "/", {
	// 			userSamplePath = userSamplePath ++ "/"
	// 		});
	// 	});
		
	// 	//fillLostSamples = iFillLostSamples;
	// 	gAttack = 0;
	// 	gRelease = 0;
	// 	lagTime = 0.1;	
		
	// 	server = Server.local;
	// 	path = Platform.userExtensionDir ++ "/cembalo/";

	// 	rates = 1!12;
	// 	masterRate = 1;

	// 	outputmapping = this.outputMappingSetup(outputmapping);

	// 	buffers = Dictionary();

	// 	if(mixToMono, {
	// 		bodySynthdef = \cembalo_player_mix_to_mono;
	// 		releaseSynthdef = \cembalo_player_oneshot_mix_to_mono;
	// 		bodySynthdefMono = \cembalo_player_mono;
	// 		releaseSynthdefMono = \cembalo_player_oneshot_mono;
	// 	}, {
	// 		bodySynthdef = \cembalo_player;
	// 		releaseSynthdef = \cembalo_player_oneshot;
	// 		bodySynthdefMono = \cembalo_player_mono_to_stereo;
	// 		releaseSynthdefMono = \cembalo_player_oneshot_mono_to_stereo;
	// 	});

	// 	// Initialize scaleType variable -- will be set in tuningSetup
	// 	"Setting up tuning...".postln;
	// 	this.setTuning(iTuning, rootFreq, 9);
	// 	"Done.".postln;

	// 	keys = nil!128;
	// 	keyEventIndex = 0!128;
	// 	currentChord = 0!128;

	// 	"Loading configuration file...".postln;
	// 	this.loadConfiguration;
	// 	"Done.".postln;

	// 	server.waitForBoot{
	// 		"Server booted.".postln;

	// 		buffersOnServer = [];
	// 		"Running query on server, finding buffers that are already loaded...".postln;
	// 		// With help from David Granström
	// 		server.cachedBuffersDo{|buf|
	// 			buffersOnServer = buffersOnServer.add(buf)
	// 		};
	// 		server.sync;
	// 		"Done.".postln;

	// 		"Loading synthdefs...".postln;
	// 		this.loadSynthDefs;
	// 		"Done".postln;

	// 		"Loading buffers...".postln;
	// 		this.loadBuffers;
	// 		server.sync;
	// 		"Done".postln;
	// 		"Number of buffers: %\n".postf(this.getNumBuffers());

	// 		this.adjustSampleOffset;

	// 		// Apply configuration to cent offsets
	// 		buffers.do{|item|
	// 			var num = item[\nn].asString;

	// 			item[\centOffset] = configuration["keys"][num]["cent"].asFloat;
	// 		};

	// 		"Loading keys...".postln;
	// 		buffers.do{|item|
	// 			var nn = item[\nn];

	// 			var output = outputmapping[nn%outputmapping.size];

	// 			keys[nn] = CembaloKey(
	// 				nn: nn,
	// 				out: output[0],
	// 				outL: output[0],
	// 				outR: output[1],
	// 				amp: gAmp,
	// 				pan: 0,
	// 				attack: gAttack,
	// 				release: gRelease,
	// 				lagTime: lagTime,
	// 				bodyBuffer: item[\body][gBodyindex],
	// 				releaseBuffer: item[\release],
	// 				bodyindex: gBodyindex,
	// 				parent: this.value()
	// 			)
	// 		};

	// 		server.sync;

	// 		"Done.".postln;

	// 		if(fillLostSamples, {
	// 			"Creating additional keys...".postln;
	// 			(0..127).do{|nn|
	// 				if(keys[nn] == nil, {
	// 					var output = outputmapping[nn%outputmapping.size];
	// 					var sampleindex = this.findClosestSample(nn);

	// 					"creating new key with note number % using sample %\n".postf(
	// 						nn, sampleindex);
						
	// 					keys[nn] = CembaloKey(
	// 						nn: nn,
	// 						out: output[0],
	// 						outL: output[0],
	// 						outR: output[1],
	// 						amp: gAmp,
	// 						pan: 0,
	// 						attack: gAttack,
	// 						release: gRelease,
	// 						bodyBuffer: buffers[sampleindex][\body][gBodyindex],
	// 						releaseBuffer: buffers[sampleindex][\release],
	// 						parent: this.value()
	// 					);
	// 				})
	// 			};
	// 			server.sync;
	// 			"Done".postln;
	// 		});

	// 		this.eventTypeSetup;

	// 		if(onLoad.class == Function, {
	// 			onLoad.value(this)
	// 		});
			
	// 		"Cembalo loaded.".postln;
	// 	}
	// }

	getMax {
		var index = 127;

		while ({buffers[index] == nil}, {
			if(buffers[index].notNil, {
				midiNoteCeil = index;
			}, {
				index = index - 1
			})
		});

		^midiNoteCeil
	}
	
	getMin {
		var index = 0;

		while ({buffers[index] == nil}, {
			if(buffers[index].notNil, {
				midiNoteOffset = index;
			}, {
				index = index + 1
			})
		});
		^midiNoteOffset
	}

	// *** Instance method: keyOn
	keyOn {|key = 60, pan = 0, amp, rate, timbre, attack, release, out, bodyindex|
		// This is how to interact with the `keyOn' method within the
		// `CembaloKey' class on the lowest abstraction level. The method
		// `makeKeyEvet' interfaces with this method.

		key = key.clip(0,127);
		pan = pan ? gPan;
		amp = amp ? gAmp;
		timbre = timbre ? gTimbre;
		attack = attack ? gAttack;
		release = release ? gRelease;
		out = out ? outputmapping[key % 12];
		bodyindex = bodyindex ? gBodyindex;

		rate = rate ? transposedRates[key % 12] * masterRate;
		
		if(keys[key].notNil, {
			keys[key].keyOn(
				newRate: rate,
				newAmp: amp,
				newPan: pan,
				newTimbre: timbre,
				newAttack: attack,
				newRelease: release,
				newOut: out,
				newBodyindex: bodyindex
			)
		}, {
			"MIDI note number % not available in current sample bank!\n".postf(key);
		});
	}

	// *** Instance method: keyOff
	keyOff {|key = 60, pan = 0, amp = 0.7, out, release, rate|

		key = key.clip(0,127);
		rate = rate ? transposedRates[key % 12] * masterRate;

		release = release ? gRelease;
		out = out ? outputmapping[key % 12];
		amp = amp ? gAmp;
		pan = pan ? gPan;

		if(keys[key].notNil, {
			keys[key].keyOff(rate, out, newRelease: release)
		}, {
			"MIDI note number % not available in current sample bank!\n".postf(key);
		});
	}

	// *** Instance method: sustainPedalOn
	sustainPedalOn {
		keys.do{|key| if(key.notNil, { key.sustainPedalOn() } ) };
	}

	// *** Instance method: sustainPedalOff
	sustainPedalOff {
		keys.do{|key| if(key.notNil, { key.sustainPedalOff() } ) };
	}

	// *** Instance method: bendKeyStep
	bendKeyStep {|key = 0, num = 0|
		var val;
		key = key.clip(0,128);
		num = num.round(1).asInteger;
		val = (transposedRates.wrapAt(key + num) * num.midiratio) / (transposedRates.wrapAt(key));
		if(keys[key].notNil, { keys[key].bend(val) } )
	}

	bendKeyStepAll {|num|
		127.do{|index|
			this.bendKeyStep(index, num)
		}
	}

	// *** Instance method: bendKey
	bendKey {|key = 0, val = 0|
		key = key.clip(0,128);
		if(keys[key].notNil, { keys[key].bend(val) } );
	}

	// *** Instance method: bendAllKeys
	bendAllKeys {|val = 0|
		keys.do{|item|
			if(item.notNil, {
				item.bend(val)
			})
		}
	}

	// // *** Instance method: lagTime_
	// setLagTime {|val = 0.1|
	// 	lagTime = val;
	// 	keys.do{|item|
	// 		item.setLagTime(lagTime)
	// 	}
	// }
	setLagTime {|val = 0.1|
		lagTime = val;
		keys.do{|item|
			if(item.notNil, {
				item.setLagTime(lagTime)
			})
		}
	}

	// *** Instance method: makeKeyEvent
	makeKeyEvent {
		| key = 60
		, dur = 4
		, delay = 0
		, pan = 0
		, rate
		, timbre
		, attack
		, release
		, out
		, bodyindex
		|

		// One level of abstraction up from `keyOn'. The user supplies a key,
		// a duration, a delay value, a pan value. If the key should be
		// fine-tuned the user can also supply a rate value, if not the
		// `keyOn' method adheres to the selected temperament.

		attack = attack ? gAttack;
		release = release ? gRelease;
		bodyindex = bodyindex ? gBodyindex;

		key = key.clip(0,128);
		if(keys[key].notNil, {
			fork {

				// By using an array of integers, I can keep track of if
				// the current event for the key has been interrupted by a
				// new event. Everytime this method is invoked, the value
				// increases by one.
				
				var localIndex = keyEventIndex[key].copy;
				
				keyEventIndex[key] = keyEventIndex[key] + 1;

				wait(delay);

				// the `keyOn' method checks if the rate is set to
				// nil. if it is, it will do all the necessary
				// transpositions for the different temperaments.
				this.keyOn(
					key,
					pan,
					rate: rate,
					timbre: timbre,
					attack: attack,
					release: release,
					out: out,
					bodyindex: bodyindex
				);
				
				wait(dur - delay);

				if(keyEventIndex[key] - 1 == localIndex, {
					this.keyOff(key, out: out, rate: rate, release: release)
				})
			}
		}, {
			"MIDI note % not available!\n".postf(key);
		})

	}

	// *** Instance method: playMIDINote
	playMIDINote {
		| note = 60
		, dur = 4
		, pan = 0
		, timbre
		, strum = 0
		, randomStrum = false
		, attack
		, release
		, out
		, bodyindex
		|

		// One level of abstraction up from `makeKeyEvent'. The user supplies
		// a MIDI note (can be an array), a duration, a panning value (can be
		// an array), a strum value (how much time there should be between
		// multiple notes), and a randomStrum value (whether or not to shuffle
		// the notes when strumming).
		
		var key = note.asArray;

		var delay = key.collect{|item, index|
			index * strum
		};

		if(randomStrum, {delay = delay.scramble});

		pan = pan.asArray;
		dur = dur.asArray;

		timbre = timbre ? gTimbre;
		attack = attack.asArray ? gAttack.asArray;
		release = release.asArray ? gRelease.asArray;
		out = out ? key.collect{|i| outputmapping.wrapAt(i)};
		bodyindex = bodyindex ? gBodyindex;

		key.do{|item, index|
			"Playing key %\n".postf(item);
			this.makeKeyEvent(
				item,
				dur.clipAt(index),
				delay.clipAt(index),
				pan.clipAt(index),
				timbre: timbre,
				attack: attack.clipAt(index),
				release: release.clipAt(index),
				out: out,
				bodyindex: bodyindex
			)
		}
	}

	// *** Instance method: playNote
	playNote {
		| freq = 440
		, dur = 4
		, pan = 0
		, timbre
		, strum = 0
		, randomStrum = false
		, attack
		, release
		, out
		, bodyindex
		|

		// As with `playMIDINote': one level of abstraction higher than
		// `makeKeyEvent'. Instead of a midi note number, the user here
		// specifies a frequency in hertz.
		
		var delay, key, rate;

		key = [];						// one array for the key index
		rate = [];						// one array for rates

		freq.asArray.do{|item, index|
			var asMIDI, midiNN;
			item = item * (440 / rootFreq);
			// convert the frequency value to midi (with decimals)
 			asMIDI = item.cpsmidi;
			// convert the midi value to integer, i.e actual midi note numbers
			midiNN = asMIDI.floor.asInteger.clip(0,127);
			// find the closest sample that can be used
			midiNN = this.findClosestSample(midiNN);
			// add the key index to the list of keys
			key = key.add(midiNN);
			// add the rate value by diffing the floored midi value
			// with the midi value, and then converting it to a ratio
			// value
			rate = rate.add((asMIDI - midiNN).midiratio)
		};
		
		delay = key.collect{|item, index|
			index * strum
		};

		if(randomStrum, {delay = delay.scramble});

		pan = pan.asArray;
		dur = dur.asArray;

		attack = attack.asArray ? gAttack.asArray;
		release = release.asArray ? gRelease.asArray;
		out = out.asArray ? key.collect{|i| outputmapping.wrapAt(i)};
		
		if(release.notNil, {
			release = release.asArray
		}, {
			release = [nil]
		});

		key.do{|item, index|
			this.makeKeyEvent(
				item,
				dur.clipAt(index),
				delay.clipAt(index),
				pan.clipAt(index),
				rate[index],
				timbre: timbre,
				attack: attack.clipAt(index),
				release: release.clipAt(index),
				out: out.clipAt(index),
				bodyindex: bodyindex
				
			)
		};
	}
	// *** Instance method: printChord
	printChord {
		var nn = [];
		currentChord.do{|item, index|
			if(item == 1, {
				nn = nn.add(index)
			})
		};
		
		"Current chord: %".format(nn).postln;
	}

	// *** Instance method: chordGate
	chordGate {|gate|
		if(gate == 1, {
			var nn = [];

			currentChord.do{|item, index|
				if(item == 1, {
					nn = nn.add(index + midiNoteOffset)
				})
			};
			this.playChord(nn)
		}, {
			this.keys.do{|item,index| this.keyOff(index)}
		})
	}
	
	// *** Instance method: playChord
	playChord {|nn, repeat=false, strum=0|

		// The user supplies an array of notes, and the Cembalo plays those
		// notes. The Cembalo saves the notes as `currentChord', so it knows
		// what notes to repeat and what notes to simply change if a new chord
		// is specified (i.e only turn off G and turn on A when changing from C
		// major to A minor).

		// However, the CembaloKey class has a timeout feature that wait until
		// the body sound has faded out. If it has, the `playChord' method
		// automatically repeats that note. This might be changed in the future.
		
		var newChord;
		var delay = 0;
		nn = nn.asArray.collect{|item| item.clip(0,127)};

		// Although the user supplies an array of MIDI note numbers,
		// the program converts that into a list of 128 values -- if
		// the note is on, it's a 1 and if it's off, it's a 0.
		
		newChord = 0!128;
		nn.do{|item|
			if(keys[nn].notNil, {
				newChord[item] = 1;
			})
		};

		newChord.do{|item, index|
			if(item == 1, {
				if(
					(currentChord[index] == 0) ||
					(repeat) ||
					(keys[index].player == nil), { // here is the timeout feature
						fork {
							var localDelay = delay.copy;
							delay = delay + strum;
							wait(localDelay);
							this.keyOn(index)

						}
					})
			}, {
				if(currentChord[index] == 1, {
					this.keyOff(index)
				})
			})
		};

		currentChord = newChord.copy;
		this.printChord();
	}

	// *** Instance method: repeatChord
	repeatChord {|randomize = 0|

		// Simply repeating the current chord.
		
		currentChord.do{|item, index|
			if(item == 1, {
				fork {
					wait(rrand(0, randomize));
					this.keyOn(index);
				}
			}, {
				this.keyOff(index)
			})
		};

		this.printChord();
	}

	// *** Instance method: transformChord
	transformChord {|function|
		if(function.isFunction, {
			var nn = [];

			currentChord.collect{|item, index|
				if(item == 1, {
					nn = nn.add(index)
				})
			};

			this.playChord(function.value(nn));
			
		})
	}

	// *** Instance method: addToChord
	addToChord {|nn, repeat = true|

		// Add a note to the current playing chord. If it is already there, it
		// just repeats it.

		nn = nn.asArray;

		nn.do{|item|
			if((currentChord[item] == 0) || (repeat == true), {
				currentChord[item] = 1;
				this.keyOn(item);
			})
		};

		this.printChord();
	}

	// *** Instance method: removeFromChord
	removeFromChord {|nn|
		nn = nn.asArray;

		nn.do{|item|
			currentChord[item] = 0;
			this.keyOff(item);
		};

		this.printChord();
	}

	// *** Instance method: allKeysOff
	allKeysOff {
		this.keys.do{|item, index|
			this.keyOff(index)
		}
	}

	// *** Instance method: setScaleRoot
	setScaleRoot {|newScaleRoot|

		// "scaleRoot" in this case means a MIDI note number, i.e
		// specifying what is considered to be 1/1 when generating a
		// scale. It is /not/ the root frequency in hertz (i.e 440,
		// 415 etc)
		
		scaleRoot = newScaleRoot % 12;
		this.tuningSetup(tuning)
	}

	// *** Instance method: setBodyindex
	setBodyindex {|bodyindex|
		gBodyindex = bodyindex.asInteger;
		keys.do{|key|
			if(key.notNil, {
				key.setBodyindex(gBodyindex)
			})
		};
	}

	// *** Instance method: setTimbre
	setTimbre {|timbre|
		gTimbre = timbre
	}

	timbre_ {|val| this.setTimbre(val)}

	// *** Instance method: setAmp
	setAmp {|amp|
		gAmp = amp;
		if(keys.notNil, {
			keys.do{|item|
				if(item.notNil, {
					item.setAmp(gAmp)
				})
			};
		});
	}

	amp_ {|val| this.setAmp(val)}

	/// *** Instance method: setAttack
	setAttack {|attack|
		gAttack = attack;
		if(keys.notNil, {
			keys.do{|item|
				if(item.notNil, {
					item.setAttack(gAttack)
				})
			}
		});
	}

	attack_ {|val| this.setAttack(val)}

	// *** Instance method: setRelease
	setRelease {|release|
		gRelease = release;
		if(keys.notNil, {
			keys.do{|item|
				if(item.notNil, {
					item.setRelease(gRelease)
				})
			}
		});
	}

	release_ {|val| this.setRelease(val)}

	// *** Instance method: setSampleCentDeviation
	setSampleCentOffset {|sample=60, val=0|
		buffers[sample][\centOffset] = val
	}

	// *** Instance method: outputMappingSetup
	outputMappingSetup {|array|
		var newArray;
		var detailed = array.isArray;

		array = array.asArray;

		array.do{|item, index|
			if(item.isArray, {
				if(item.size > 1, {
					newArray = newArray.add([item[0], item[1]])
				}, {
					newArray = newArray.add([item[0], item[0]])
				})
			}, {
				if(detailed, {
					newArray = newArray.add([item, item])
				}, {
					newArray = newArray.add([item, item + 1])
				})
			})
		};
		
		^newArray
	}

	// *** Instance method: setOutputmapping
	setOutputmapping {|array|
		outputmapping = this.outputMappingSetup(array);

		if(buffers.notNil, {
			buffers.do{|item|
				var nn = item[\nn];

				var output = outputmapping[nn%outputmapping.size];

				keys[nn].output_(output[0]);
				keys[nn].outputL_(output[0]);
				keys[nn].outputR_(output[1]);
			};
		});
	}

	outputmapping_ {|val| this.setOutputmapping(val)}

	// *** Instance method: eventTypeSetup
	eventTypeSetup {
		Event.removeEventType(\cembalo);
		Event.removeEventType(\cembalomidi);

		Event.addEventType(\cembalo, {
			if(~cembalo.notNil, {
				~play = ~cembalo.playNote(
					freq: ~freq.value,
					dur: ~sustain.value,
					strum: ~strum.value,
					pan: ~pan,
					timbre: ~timbre,
					randomStrum: ~randomStrum,
					attack: ~attack,
					release: ~release,
					out: ~out,
					bodyindex: ~bodyindex
				);
			}, {
				~play = "You have to supply an instace of Cembalo".postln
			})
		}, (
			randomStrum: false,
			timbre: 0,
			attack: 0,
			release: 0,
			out: nil,
			bodyindex: 0
		));
		"Done.".postln;

		Event.addEventType(\cembalomidi, {
			if(~cembalo.notNil, {
				~play = ~cembalo.playMIDINote(
					note: ~midinote.value,
					dur: ~sustain.value,
					strum: ~strum.value,
					pan: ~pan,
					timbre: ~timbre,
					randomStrum: ~randomStrum,
					attack: ~attack,
					release: ~release,
					out: ~out,
					bodyindex: ~bodyindex
				);
			}, {
				~play = "You have to supply an instace of Cembalo".postln
			})
		}, (
			randomStrum: false,
			timbre: 0,
			attack: 0,
			release: 0,
			out: nil,
			bodyindex: nil
		));
	}

	// *** Instance method: generateValotti
	generateValotti {
		var scale = [1];
		var comma = (3/2).pow(12) / 2.pow(7);

		// First, add the notes C-B (all 1/6 comma fifths)(
		5.do{
			var fifth = 1.5 / comma.pow(1/6);
			scale = scale.add(scale[scale.size - 1] * fifth);
		};

		// Then, add the notes F# - F (all pure fifths)
		6.do{
			var fifth = 1.5;
			scale = scale.add(scale[scale.size - 1] * fifth)
		};

		// Force the intervals into one octave:
		scale = scale.collect{|ratio|
			while({ratio > 2}, {
				ratio = ratio / 2
			});
			ratio
		};

		scale = scale.sort;

		^scale
	}

	// *** Instance method: generateMeantoneTemperament
	generateMeantoneTemperament {|fractionOfComma=0|
		var comma = 81/80;

		^this.generateFifthBasedScale(1.5 / comma.pow(fractionOfComma));
	}

	// *** Instance method: generateFifthBasedScale
	generateFifthBasedScale {|fifth=1.5|
		var scale = [1];
		var i = 0;
		
		// first, scale goes up in fifths:

		// index:   0 1 2 3 4 5  6  7
		// name:    G D A E B F# C# G#
		while( {i < 8}, {
			var newRatio = scale[i] * fifth;
			scale = scale.add(newRatio);
			i = i + 1
		} );

		// the, scale goes down in fifths:

		// index:  7 8  9
		// name:   F Bb Eb
		while( {i < 11}, {
			if(i == 8, {
				var newRatio = 1 / fifth;
				scale = scale.add(newRatio);
				i = i + 1
			}, {
				var newRatio = scale[i] / fifth;
				scale = scale.add(scale[i] / fifth);
				i = i + 1
				
			});
		});
		scale = scale.collect{|item|
			while({ (item > 2) || (item < 1) }, {
				if(item > 2, {
					item = item / 2
				}, {
					item = item * 2
				})
			});
			item
		};
		scale = scale.sort;
		^scale
	}

	// *** Instance method: setRootFreq
	setRootFreq {|hertz, index = 9|
		var ratios, ratio;
		"Setting root freq to %\n".postf(hertz);

		index = index ? 9;
		
		ratios = this.tuningAsArray;
		ratio = ratios[9] / ratios[index];
		rootFreq = hertz * ratio;
		this.adjustSampleOffset;
	}

	rootFreq_ {|val| this.setRootFreq(val)}

	// *** Instance method: adjustSampleOffset
	adjustSampleOffset {
		// Hitta bråket för tonen A i den nuvarande stämningen
		var ratio = this.tuningAsArray()[9];
		// Hitta frekvensen av konsertstämt C genom att dividera 440
		// Hz med avståndet mellan C och A i et12
		var concertC = 440 / 9.midiratio;
		var diff, sampleOffset;
		var oldOffset;

		// Dividera den önskade grundfrekvensen med bråket för tonen A
		// för att finna vad tonen C har för frekvens i den nuvarande
		// stämningen, och dividera det med det konsertstämda C för
		// att få ett flyttal som symboliserar avvikelsen mellan C i
		// et12 i A=440 Hz och C i den givna stämningen i A=rootFreq
		// Hz
		masterRate = (rootFreq / ratio) / concertC;

		// Räkna ut denna differans i MIDI genom att köra .ratiomidi
		diff = masterRate.ratiomidi;

		// Runda av detta tal för att hitta en sampleOffset, så att vi
		// till slut använer rätt sampling för rätt ton
		sampleOffset = diff.floor.asInteger;

		masterRate = (diff - sampleOffset).midiratio;
		
		oldOffset = bufferIndexOffset;

		bufferIndexOffset = sampleOffset;

		sampleOffset = sampleOffset - oldOffset;

		// Function that iterates over all the buffers and offsets
		// them sampleOffset number of steps.
		if(sampleOffset > 0, {
			127.do{|index|
				buffers[index - sampleOffset] = buffers[index]
			}
		}, {
			(127..0).do{|index|
				buffers[index - sampleOffset] = buffers[index]
			}
		});
	}
	
	// *** Instance method: setTuning
	setTuning {|newTuning, scaleRoot, rootFreq, rootFreqIndex|
		tuning = newTuning;
		if(scaleRoot.notNil, {
			this.setScaleRoot(scaleRoot)
		}, {
			this.tuningSetup(tuning);
		});

		if(rootFreq.notNil, {
			this.setRootFreq(rootFreq, rootFreqIndex)
		})
	}

	tuning_ {|val| this.setTuning(val)}

	// *** Instance method: tuningSetup
	tuningSetup {|newTuning|
		tuning = newTuning;
		if(newTuning.isArray, {
			var len = newTuning.size;
			
			if(len < 12, {
				var diff = 12 - len;
				"not enough ratios: adding % 2".format(diff).postln;
				diff.do{
					newTuning = newTuning.add(2);
				}
			}, {
				if(len > 12, {
					var diff = len - 12;
					"too many ratios! removing % ratios".format(diff).postln;
					diff.do{|index|
						newTuning.removeAt(len - index - 1)
					};
				});
			});

			rates = newTuning.collect{|item, index|
				if(index == 0, {
					1
				}, {
					item / index.midiratio
				})
			};

		}, {
			if(newTuning.isNumber, {
				var scale = this.generateMeantoneTemperament(newTuning);
				this.tuningSetup(scale);
			}, {
				switch(newTuning,
					'et12', {
						var scale = 12.collect{|i|
							//2.pow(i / 12)
							i.midiratio
						};
						"Loading 12-tone equal temperament tuning...".postln;
						this.tuningSetup(scale);
					},
					'mean', {
						var scale = this.generateMeantoneTemperament(1/4);
						"Loading quarter-comma meantone tuning...".postln;
						this.tuningSetup(scale);
					},
					'mean6', {
						var scale = this.generateMeantoneTemperament(1/6);
						"Loading sixth-comma meantone tuning...".postln;
						this.tuningSetup(scale);
					},
					'pyth', {
						var scale = this.generateMeantoneTemperament(0);
						"Loading pythagorean tuning...".postln;
						this.tuningSetup(scale)
					},
					'fivelimit', {
						"Loading five-limit tuning...".postln;
						this.tuningSetup([
							1,
							16/15,
							9/8,
							6/5,
							5/4,
							4/3,
							45/32,
							3/2,
							8/5,
							5/3,
							9/5,
							15/8
						])
					},
					'sevenlimit', {
						"Loading seven-limit tuning...".postln;
						this.tuningSetup([
							1,
							16/15,
							9/8,
							7/6,
							5/4,
							4/3,
							7/5,
							3/2,
							14/9,
							5/3,
							7/4,
							15/8
						])
					},
					'w3', {
						"Loading werckminster III tuning...".postln;
						this.tuningSetup([
							1,
							256/243,
							(64/81) * 2.pow(1/2),
							32/27,
							(256/243) * 2.pow(1/4),
							4/3,
							1024/729,
							(8/9) * (2.pow(3)).pow(1/4),
							128/81,
							(1024/729) * 2.pow(1/4),
							16/9,
							(128/81) * 2.pow(1/4)
						])
					},
					'valotti', {
						var scale = this.generateValotti();
						"Loading Valotti Well Temperament...".postln;
						this.tuningSetup(scale);
					},
					{
						"Tuning % not found! Using default et12.\n".postf(newTuning);
						this.tuningSetup('et12');
					}
				)
			})
		});
		transposedRates = rates.rotate(scaleRoot % 12);
		this.adjustSampleOffset();
	}

	setTuningPerKey {|index = 0, ratio = 1|
		tuning[index % tuning.size] = ratio;
		this.tuningSetup(tuning);
	}

	// *** Instance method: tuningAsArray
	tuningAsArray {
		^transposedRates.collect{|item, index|
			index.midiratio * item
		};
	}

	// *** Instance method: getKeyFreq
	getKeyFreq {|key = 0|
		var octave = 2.pow(key.div(12) - 69.div(12));
		var ratios = this.tuningAsArray();
		var ratio = ratios[key % ratios.size] / ratios[9];

		^rootFreq * ratio * octave
	}

	// *** Instance method: arrayContains
	arrayContains {|array,value|
		var contains = false;
		array.asArray.do{|item|
			if(item == value, {contains = true})
		};

		^contains
	}

	// *** Instance method: findClosestKey
	findClosestKey {|key = 60|
		// initialize an index
		var i = 1;

		// if the key does in fact exist, return it
		if(keys[key].notNil, {
			^key
		});

		// as long as either one of the indexes we're looking at are
		// in the specified range, keep looking
		while({ (key - i >= 0) || (key + i < 128)}, {
			// if a key under the key supplied is not nil, return it
			if(keys[key - i].notNil, {
				^(key - i)
			}, {
				// same goes for a key over
				if(keys[key + i].notNil, {
					^(key + i)
				}, {
					// if not, search wider.
					i = i + 1;
				})
			})
		});
	}

	// *** Instance method: findClosestSample
	findClosestSample {|key = 60|
		var i = 1;

		if(buffers[key].notNil, {
			^key
		});

		while({ (key - i >= 0) || (key + i < 128)}, {

			if(buffers[key - i].notNil, {
				^(key - i)
			}, {
				if(buffers[key + i].notNil, {
					^(key + i)
				}, {
					i = i + 1
				})
			})
		})
	}
	
	// *** Instance method: loadSynthDefs
	loadSynthDefs {
		// *** SynthDef: cembalo_player
		SynthDef(\cembalo_player, {
			| buf = 0
			, gate = 1
			, outL = 0
			, outR = 1
			, rate = 1
			, pan = 0
			, amp = 1
			, atk = 0
			, rel = 0
			, lagTime = 0.1
			, hpfCutoff = 20
			|

			var env = EnvGen.kr(Env.asr(atk,1,rel), gate, doneAction:2);
			var sig = PlayBuf.ar(2, buf, rate.lag(lagTime) * BufRateScale.kr(buf)) * env;

			sig = Balance2.ar(sig[0], sig[1], pan);
			sig = HPF.ar(sig, hpfCutoff);

			sig = sig * amp.lag();

			Out.ar(outL, sig[0]);
			Out.ar(outR, sig[1])
		}).add;

		// *** SynthDef: cembalo_player_oneshot
		SynthDef(\cembalo_player_oneshot, {
			| buf = 0
			, outL = 0
			, outR = 1
			, rate = 1
			, pan = 0
			, amp 1
			, hpfCutoff = 20
			|

			var sig = PlayBuf.ar(2, buf, rate, doneAction:2);
			sig = Balance2.ar(sig[0], sig[1], pan);
			sig = HPF.ar(sig, hpfCutoff);
			sig = sig * amp.lag();

			Out.ar(outL, sig[0]);
			Out.ar(outR, sig[1]);
		}).add;

		// *** SynthDef: cembalo_player_mono
		SynthDef(\cembalo_player_mono_to_stereo, {
			| buf = 0
			, gate = 1
			, outL = 0
			, outR = 1
			, rate = 1
			, pan = 0
			, amp = 1
			, atk = 0
			, rel = 0
			, lagTime = 0.1
			, hpfCutoff = 20
			|

			var env = EnvGen.kr(Env.asr(atk,1,rel), gate, doneAction:2);
			var sig = PlayBuf.ar(1, buf, rate.lag(lagTime) * BufRateScale.kr(buf)) * env;
			sig = HPF.ar(sig, hpfCutoff);

			sig = Pan2.ar(sig, pan);


			sig = sig * amp.lag();

			Out.ar(outL, sig[0]);
			Out.ar(outR, sig[1])
		}).add;

		// *** SynthDef: cembalo_player_oneshot
		SynthDef(\cembalo_player_oneshot_mono_to_stereo, {
			| buf = 0
			, outL = 0
			, outR = 1
			, rate = 1
			, pan = 0
			, amp 1
			, hpfCutoff = 20
			|

			var sig = PlayBuf.ar(1, buf, rate, doneAction:2);
			sig = HPF.ar(sig, hpfCutoff);
			sig = Pan2.ar(sig, pan);
			sig = sig * amp.lag();

			Out.ar(outL, sig[0]);
			Out.ar(outR, sig[1]);
		}).add;

		// *** SynthDef: cembalo_player_mix_to_mono
		SynthDef(\cembalo_player_mix_to_mono, {
			| buf = 0
			, gate = 1
			, out = 0
			, rate = 1
			, pan = 0
			, amp = 1
			, atk = 0
			, rel = 0
			, lagTime = 0.1
			, hpfCutoff = 20
			|

			var env = EnvGen.kr(Env.asr(atk,1,rel), gate, doneAction:2);
			var sig = PlayBuf.ar(2, buf, rate.lag(lagTime) * BufRateScale.kr(buf)) * env;
			
			sig = Mix(sig);
			sig = HPF.ar(sig, hpfCutoff);

			sig = sig * amp.lag();

			Out.ar(out, sig)
		}).add;

		// *** SynthDef: cembalo_player_oneshot_mix_to_mono
		SynthDef(\cembalo_player_oneshot_mix_to_mono, {
			| buf = 0
			, out = 0
			, rate = 1
			, pan = 0
			, amp = 1
			, hpfCutoff = 20
			|

			var sig = PlayBuf.ar(2, buf, rate, doneAction:2);
			
			sig = Mix(sig);
			sig = HPF.ar(sig, hpfCutoff);
			
			sig = sig * amp.lag();

			Out.ar(out, sig)
		}).add;

		// *** SynthDef: cembalo_player_mono
		SynthDef(\cembalo_player_mono, {
			| buf = 0
			, gate = 1
			, out = 0
			, rate = 1
			, pan = 0
			, amp = 1
			, atk = 0
			, rel = 0
			, lagTime = 0.1
			, hpfCutoff = 20
			|

			var env = EnvGen.kr(Env.asr(atk,1,rel), gate, doneAction:2);
			var sig = PlayBuf.ar(1, buf, rate.lag(lagTime) * BufRateScale.kr(buf)) * env;
			sig = HPF.ar(sig, hpfCutoff);
			sig = sig * amp.lag();

			Out.ar(out, sig)
		}).add;

		// *** SynthDef: cembalo_player_oneshot_mono
		SynthDef(\cembalo_player_oneshot_mono, {
			| buf = 0
			, out = 0
			, rate = 1
			, pan = 0
			, amp = 1
			, hpfCutoff = 20
			|

			var sig = PlayBuf.ar(1, buf, rate, doneAction:2);
			sig = HPF.ar(sig, hpfCutoff);
			sig = sig * amp.lag();

			Out.ar(out, sig)
		}).add;
	}

	// *** Instance method: loadConfiguration
	loadConfiguration {|userSamplePath|
		if(userSamplePath.notNil, {
			"Found configuration in supplied path!".postln;
			configurationPath = userSamplePath ++ "info.json"
		}, {
			configurationPath = path ++ "samples/info.json"
		});

		if(File.exists(configurationPath), {
			var file = File.new(configurationPath, "r").readAllString;
			"Found file in configuration path!".postln;
			configuration = file.parseJSON;
		}, {
			var file;
			configurationPath = path ++ "samples/info.json";

			file = File.new(configurationPath, "r").readAllString;
			configuration = file.parseJSON;
		});
	}

	// *** Instance method: loadBuffers
	loadBuffers {|userSamplePath|
		var bodyPath, releasePath;

		if(userSamplePath.notNil, {
			"using supplied path %\n".postf(userSamplePath);
			bodyPath = userSamplePath ++ "bod/*.wav";
			releasePath = userSamplePath ++ "rel/*.wav";
		}, {
			bodyPath = path ++ "samples/bod/*.wav";
			releasePath = path ++ "samples/rel/*.wav";
		});

		bodyPath.pathMatch.do{|filePath|
			var num = filePath.findRegexp(".{3}(?=.wav)")[0][1].compile.value();
			var buffer = this.cembaloLoadBuffer(filePath);

			if(buffers[num] == nil, {
				buffers[num] = Dictionary.newFrom([\nn, num, \body, [buffer], \centOffset, 0]);
			}, {
				buffers[num][\body] = buffers[num][\body] ++ buffer
			});
		};

		releasePath.pathMatch.do{|filePath|
			var num = filePath.findRegexp(".{3}(?=.wav)")[0][1].compile.value();
			var buffer = this.cembaloLoadBuffer(filePath);

			buffers[num][\release] = buffer;
		};
	}

	/// *** Instance method: cembaloLoadBuffer
	cembaloLoadBuffer {|path|
		// With help from David Granström
		buffersOnServer.do{|buf|
			if(path == buf.path, {
				^buf
			})
		};
		^Buffer.read(server, path)
	}

	// *** Instance method: getNumBuffers
	getNumBuffers {
		var num = 0;
		buffers.do{|item|
			if(item.notNil, {
				num = num + item[\body].size;
				if(item[\release].notNil, {
					num = num + 1
				})
			})
		};

		^num;
	}

	// *** Instance method: loadKeys
	loadKeys {|buffers|
		buffers.do{|item|
			var nn = item[\nn];

			var output = outputmapping[nn%outputmapping.size];

			keys[nn] = CembaloKey(
				nn: nn,
				out: output[0],
				outL: output[0],
				outR: output[1],
				amp: gAmp,
				pan: 0,
				attack: gAttack,
				release: gRelease,
				lagTime: lagTime,
				bodyBuffer: item[\body][gBodyindex],
				releaseBuffer: item[\release],
				bodyindex: gBodyindex,
				parent: this.value()
			)
		};
	}

	// *** Instance method: free
	free {
		buffers.do{|buf|
			if(buf[\release].notNil, {
				buf[\release].free;
			});

			buf[\body].do(_.free);
		};
	}
}
// Local Variables:
// eval: (outshine-mode 1)
// End: