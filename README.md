A harpsichord and sample player in SuperCollider.

# Installation

Clone this repository into the SuperCollider extensions folder, found by running `Platform.userExtensionDir` in SuperCollider.

# Usage

Documented in the included help file, but some examples are included in this readme. This class can be used as a virtual harpsichord using the included samples, but it can also be used with whatever samples you may have.

# Providing your own samples

You can provide your own samples to use with `Cembalo`. These must be put in a folder with a specific folder structure:

```
folder
├── info.json
├── bod
│   ├── nn.wav
│   ├── nn.wav
│   ├── etc.
└── rel
    ├── nn.wav
    ├── nn.wav
    └── etc.
```

Where `nn` is the midi note number of the specific sample, expressed as **three** integers (i.e `060` for middle C). You have to supply samples in the `bod` directory -- this is the \"body\" of the sound. The samples in the ` directory is what\'s played when releasing a key, and are not required.

The file `info.json` is where all configuration is stored. If no configuration file is present, a default one will be used. The configuration file should use the following structure:

```javascript
{
    keys: {
    <nn>: {
        "cent": <cent deviation>
    },
    <nn2>: {
        "cent": <cent deviation>
    }
    }
}
```

So, if you know for example that you D above middle C is 4 cents flat, input that and `Cembalo` will compensate when playing back the sample.

Now, if you want to load your own samples just pass the path to the directory to the `userSamplePath` argument:

```supercollider
c = Cembalo(userSamplePath:"path/to/dir/")
```

# Regarding meantone (and other fifth-based tunings)

The traditional way to generate a meantone temperament (or any temperament based on fifths, for that matters) is by starting from the note D. This takes us around the circle of fiths like this:

  Number of fifths from center   Note
  ------------------------------ ------
  6                              G\#
  5                              C\#
  4                              F\#
  3                              B
  2                              E
  1                              A
  0                              D
  -1                             G
  -2                             C
  -3                             F
  -4                             Bb
  -5                             Eb

If we express the note C as center, the table would look like this:

  Number of fifths from center   Note
  ------------------------------ ------
  8                              G\#
  7                              C\#
  6                              F\#
  5                              B
  4                              E
  3                              A
  2                              D
  1                              G
  0                              C
  -1                             F
  -2                             Bb
  -3                             Eb

...and that is how fifth-based scales are generated in `Cembalo`. 8 fifths up, 3 fifths down. If for example the note G would be considered as the root, we would only reach Bb going down and reach all the way to D\# going up.

The two far edges of the table (Eb and G\#) form the *wolf fifth*,  is considerably narrower than a pure fifth. This can be exploited. Some other intervals are also pretty far from \"pure\" (in the western sense). Most notably are the major thirds G\#-C and F\#-Bb, the minor thirds Eb-F\# and Bb-C\# as well as the close-to-septimal sevenths Bb-G\# and Eb-C\#.

These \"special\" intervals will be on the same locations no matter what fifth-based temperament is used (except for equal temperament), it\'s just the sizes of the intervals that differ. A meantone wolf fifth sounds different than a pythagorean wolf fifth -- in fact, the major third F\#-Bb is the most \"in tune\" major third of the pythagorean temperament.

# Regarding the `timbre` parameter

When playing a note using `keyOn`, `playMIDINote` or `playNote`, the user can supply a `timbre` parameter. This can range from -1 to 1. When supplying a positive number, the sampler will choose a sample with a lower pitch than the specified note and pitch it up, increasing the high frequency content. On the other hand, when supplying a negative number, the sampler will choose a sample of a higher pitch than the specified note and pitch it down, *decreasing* the high frequency content. If no sample is found at the specified index (+ 32 from the specified note if
`timbre` is set to 1), the sampler will choose the closest found sample. This means that there\'s fixed floor and ceiling to the `timbre` parameter, which will get closer to the specified note the lower/higher you go.

# Examples
## Playing patterns

When loading the class, a new event type is also loaded: `\cembalo`. It is used as follows:

```supercollider
// define an instance of `Cembalo'
c = Cembalo()

// `Pbindef' using the custom event type `\cembalo':
(
Pbindef(\cembalo,
    \type, \cembalo,
    \cembalo, c,
    \freq, 75 *
    Prand([
        1,
        5/4,
        3/2,
        7/4
    ],inf) *
    Prand([1,3/2,2],inf) *
    Prand([
        1,
        [0.5,1],
        [1,3/2],
        [1,3/2,2],
        [1,3/2,9/4]
    ],inf),
    \strum, Pwhite(0.1,0.3),
    \randomStrum, true,
    \panDispersion, 0.8,
    \dur, Pwhite(0.6,2.0),
    \legato, 4
).play
)

// stop the `Pbindef'
Pbindef(\cembalo).stop;
```

# MIDI Input

Simply call the `.keyOn` method inside of a `MIDIFunc`. If the sample doesn\'t exist, SuperCollider will tell you.

``` supercollider
(
// First, initialize a `Cembalo'
c = Cembalo();
)

(
// The initialize MIDI
MIDIClient.init;
MIDIIn.connectAll;

// Finally, add MIDI functions for note on/note off messages
MIDIFunc.noteOn({|val, num| c.keyOn(num)});
MIDIFunc.noteOff({|val, num| c.keyOff(num)});
)

// You can now experiment with how different tunings sound like:
c.tuning_('mean')                       // quarter-comma meantone tuning (look out for Bb-G#!)
c.tuning_('pyth')                       // pythagorean tuning
c.tuning_('sevenlimit')                 // basic seven limit just intonation

c.tuning_(1/6)                          // sixth-comma meantone tuning
c.tuning_(2/7)                          // 2/7-comma meantone tuning
```
