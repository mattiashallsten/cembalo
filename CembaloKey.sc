CembaloKey {
	var <nn, out, outL, outR, amp, pan, <bodyBuffer, <releaseBuffer, parent;
	var <player, playerTimer, keyIsDepressed = false, rate = 1, bendAm = 0, timbre = 0, compRate = 1;
	var bodyLength;

	// * Class method: *new
	*new {
		| nn = 24
		, out = 0
		, outL = 0
		, outR = 1
		, amp = 0.7
		, pan = 0
		, bodyBuffer = 0
		, releaseBuffer = 0
		// , bodySynthdef = nil
		// , releaseSynthdef = nil
		// , bodySynthdefMono = nil
		// , releaseSynthdefMono = nil
		, parent = nil
		|

		^super.newCopyArgs(
			nn,
			out,
			outL,
			outR,
			amp,
			pan,
			bodyBuffer,
			releaseBuffer,
			// bodySynthdef,
			// releaseSynthdef,
			parent
		).initCembaloKey;		
	}

	initCembaloKey {
		bodyLength = bodyBuffer.numFrames / bodyBuffer.sampleRate;
	}

	// * Instance method: keyOn
	keyOn {|newRate, newAmp, newPan, newTimbre = 0|

		if(newRate.notNil, {
			rate = newRate
		});
		if(newAmp.notNil, {
			amp = newAmp
		});
		if(newPan.notNil, {
			pan = newPan
		});
		timbre = newTimbre;
		
		if(keyIsDepressed, {
			this.keyOff;
		});

		// make adjustments in playback rate (set value of compRate)
		this.applyTimbre;
		
		
		//		"turning on key %".format(nn).postln;
		if(bodyBuffer.numChannels == 2, {
			player = Synth(parent.bodySynthdef, [
				\buf, bodyBuffer,
				\out, out,
				\outL, outL,
				\outR, outR,
				\rate, rate * bendAm.midiratio * compRate,
				\pan, pan,
				\amp, amp
			]);
		}, {
			player = Synth(parent.bodySynthdefMono, [
				\buf, bodyBuffer,
				\out, out,
				\outL, outL,
				\outR, outR,
				\rate, rate * bendAm.midiratio * compRate,
				\pan, pan,
				\amp, amp
			]);
		});
			
		
		playerTimer = fork { wait(bodyLength - 0.1); player.set(\gate, 0); player = nil };
		
		keyIsDepressed = true;
	}
	
	// * Instance method: keyOff
	keyOff {
		if(keyIsDepressed, {
			//"turning off key %".format(nn).postln;

			if(playerTimer.notNil, { playerTimer.stop } );
			
			if(player.notNil, {
				player.set(\gate, 0);
				player = nil;
			});

			if(releaseBuffer.notNil, {
				if(releaseBuffer.numChannels == 2, {
					Synth(parent.releaseSynthdef, [
						\buf, releaseBuffer,
						\out, out,
						\outL, outL,
						\outR, outR,
						\rate, rate * bendAm.midiratio * compRate,
						\pan, pan,
						\amp, amp
					]);
				}, {
					Synth(parent.releaseSynthdefMono, [
						\buf, releaseBuffer,
						\out, out,
						\outL, outL,
						\outR, outR,
						\rate, rate * bendAm.midiratio * compRate,
						\pan, pan,
						\amp, amp
					]);
				});
			});

			keyIsDepressed = false
		});

		this.bend(0);
	}

	// * Instance method: applyTimbre
	applyTimbre {
		var sampleindex, adjusted_rate;
		// remap timbre value to -32 <-> 32
		sampleindex = (timbre * (-32)).asInteger;
		// add remapped value to nn -- get the sample to play
		sampleindex = (nn + sampleindex).clip(parent.midiNoteOffset, parent.midiNoteCeil);
		// get ratio between new index and target pitch
		adjusted_rate = (nn - sampleindex).midiratio;
		
		// subtract the midioffset of the Cembalo
		sampleindex = sampleindex - parent.midiNoteOffset;
		
		bodyBuffer = parent.bodyBuffers[sampleindex];

		if(releaseBuffer.notNil,{
			releaseBuffer = parent.releaseBuffers[sampleindex];
		});
		
		compRate = adjusted_rate;
	}

	bend {|val|
		bendAm = val;
		if(player.notNil, {
			player.set(\rate, rate * bendAm.midiratio * compRate)
		})
	}

	// * Instance method: rate_
	rate_ {|val|
		rate = val;
	}

	// * Instance method: amp_
	amp_ {|val|
		out = val
	}
}
// Local Variables:
// eval: (outshine-mode 1)
// End: