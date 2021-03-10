// To play `Cembalo' via MIDI, just use the .noteOn method:

(
// First, initialize a `Cembalo'
c = Cembalo();
)

(
// The initialize MIDI
MIDIClient.init;
MIDIIn.connectAll;

// Finally, add MIDI functions for note on/note off messages
MIDIFunc.noteOn({|val, num| c.noteOn(num)});
MIDIFunc.noteOff({|val, num| c.noteOff(num)});
)

// You can now experiment with how different tunings sound like:
c.tuning_('et12') 						// quarter-comma meantone tuning (look out for Bb-G#!)
c.tuning_('pyth')						// pythagorean tuning
c.tuning_('sevenlimit')					// basic seven limit just intonation

c.tuning_(1/6)							// sixth-comma meantone tuning
c.tuning_(2/7)							// 2/7-comma meantone tuning

