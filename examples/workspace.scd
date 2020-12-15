c = Cembalo(0, 'et12', 0, 0.3)

c.keyOn(10)
c.keyOff(10)
c.releaseBuffers[0].play

Synth(\cembalo_player_oneshot, [\buf, c.releaseBuffers[0]])
c.playNote(220)
c.root_(4)
c.tuning_('sevenlimit')
c.tuning_('pyth')
t = 8

c.playNote(220 * [1,3/2,7/4], 5)
c.keys

c.playMIDINote([60,64,67,72], 8, 0.04, true, 0.5)
c.playMIDINote([53,57,60,65], 8, 0.04, true, 0.5)
c.playMIDINote([56,48,63], 8, 0.04, true, 0.5)
c.playMIDINote([43,50,55,58],8, 0.04, true, 0.5)
c.playMIDINote([48,52,55,60],8, 0.04, true, 0.5)
c.playMIDINote([49,52,55,58],8, 0.04, true, 0.5)
c.playMIDINote([50,53,57,64],8, 0.04, true, 0.5)
c.playMIDINote([36,43,60,62,67,70], 8, 0.04, true, 0.5)
c.amp_(1)
c.root_(0)

c.playMIDINote(60, 2, 0, false, 0)

// kotsu
c.playMIDINote([57, 64, 69, 71, 76, 78], t, 0.1, true, 1)
// ichi
c.playMIDINote([59, 62, 64, 69, 71, 78], t, 0.02)
// ku
c.playMIDINote([61, 62, 64, 68, 69, 71], t, 0.02)
// bo
c.playMIDINote([62, 64, 69, 71, 76, 78], t, 0.02)
// otsu
c.playMIDINote([64, 69, 71, 74, 76, 78], t, 0.02)
// ge
c.playMIDINote([66, 68, 69, 71, 74, 78], t, 0.02)
// ju
c.playMIDINote([67, 69, 71, 74, 76], t, 0.02)
// ju II
c.playMIDINote([66, 67, 69, 71, 74, 76], t, 0.02)
// bi
c.playMIDINote([68, 69, 71, 72, 74, 78], t, 0.02)
// gyo
c.playMIDINote([69, 71, 74, 76, 78], t, 0.02)
// hi
c.playMIDINote([69, 71, 72, 74, 76, 78], t, 0.02)

x = [1, 2, 3, 4, 5]
x.rotate(1).postln;
x
c.root
c.root_(0)
c.playNote(95 * [1,3/2,11/10,7/2,2,7/3], 10, 0.1, true)
(
var notes = (24..108);
var time = 0.3;
notes.do{|item, i|
	fork {
		wait(i * time * 1.2);
		x.playMIDINote(item, time)
	}
}
)

x.playMIDINote([35,65,74], 2)

i = [1,2,3]
j = i.copy
j = j.scramble
j
i


x.bodyBuffers.size

1.midiratio / (16/15)
(9/8) / 2.midiratio

z = [1,2,3]
(
~transposeArray = {|array, num|
	var localArray = array.asArray;
	num.do{
		localArray = localArray.addFirst(localArray[localArray.size - 1]);
		localArray.removeAt(localArray.size - 1);
	};

	localArray;
}
)

z = (0..11)
~transposeArray.(z, 7)
z
z.slide(3,1)

[1, 2, 3, 4, 5, 6].slide(3, 1).postcs;
[1, 2, 3, 4, 5, 6].slide(3, 2).postcs;
[1, 2, 3, 4, 5, 6].slide(4, 1).postcs;

