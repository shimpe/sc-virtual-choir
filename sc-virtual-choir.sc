s.boot;

(
var table;
var gap = 40;
var mapped, mapped2, diffbuf, diffbuf2;
var miditoname;
var nametomidi;
var sound;
var difference, difference2;
var tf, tf2;

// define a function to convert a midi note number to a midi note name
miditoname = ({ arg note = 60, style = \American ;
		var offset = 0 ;
		var midi, notes;
		case { style == \French } { offset = -1}
			{ style == \German } { offset = -3} ;
		midi = (note + 0.5).asInteger;
		notes = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"];

		(notes[midi%12] ++ (midi.div(12)-1+offset))
});

// define a function to convert a midi note name to a midi note number
nametomidi = ({ arg name = "C4", style = \American ;
		var offset = 0 ; // French usage: +1 ; German usage: +3
		var twelves, ones, octaveIndex, midis;

		case { style == \French } { offset = 1}
			{ style == \German } { offset = 3} ;

		midis = Dictionary[($c->0),($d->2),($e->4),($f->5),($g->7),($a->9),($b->11)];
		ones = midis.at(name[0].toLower);

		if( (name[1].isDecDigit), {
			octaveIndex = 1;
		},{
			octaveIndex = 2;
			if( (name[1] == $#) || (name[1].toLower == $s) || (name[1] == $+), {
				ones = ones + 1;
			},{
				if( (name[1] == $b) || (name[1].toLower == $f) || (name[1] == $-), {
					ones = ones - 1;
				});
			});
		});
		twelves = (name.copyRange(octaveIndex, name.size).asInteger) * 12;

		(twelves + 12 + ones + (offset*12))
});

// define a table of reference notes [c c# d ... b]
table = Array.fill(12, {arg i; i + 60}); // [60,61,...,71]
// define a table of mapped notes (Default values)
mapped = [nametomidi.value("g3"),
	   nametomidi.value("a3"),
	   nametomidi.value("b3"),
	   nametomidi.value("bb3"),
	   nametomidi.value("c4"),
	   nametomidi.value("c4"),
	   nametomidi.value("a3"),
	   nametomidi.value("d4"),
	   nametomidi.value("e4"),
	   nametomidi.value("f4"),
	   nametomidi.value("f4"),
	   nametomidi.value("g4"),
];
mapped2 = [nametomidi.value("e3"),
	   nametomidi.value("g3"),
	   nametomidi.value("g3"),
	   nametomidi.value("g#3"),
	   nametomidi.value("a3"),
	   nametomidi.value("a4"),
	   nametomidi.value("d4"),
	   nametomidi.value("b4"),
	   nametomidi.value("c4"),
	   nametomidi.value("d4"),
	   nametomidi.value("d4"),
	   nametomidi.value("d4"),
];

// define a table to store the difference between reference and mapped note
difference = Array.fill(table.size, {0});
// define a buffer on the server for consultation from the SynthDef
diffbuf= Buffer.loadCollection(s,table,action:{|msg| msg.postln;});
difference2= Array.fill(table.size, {0});
diffbuf2=Buffer.loadCollection(s,table,action:{|msg| msg.postln;});
tf = List.new(table.size);
tf2 = List.new(table.size);

// define a window called "Setup mapping"
w = Window.new("Setup mapping", Rect(200,200,15*gap,190));
// add the reference notes as labels (fixed), and the mapped notes as text fields (editable)
// whenever a text field is updated, update the list of mapped notes (mapped)
table.do({arg item, i;
	var t, u;
	StaticText(w, Rect(10+gap*i, 10, gap, 30)).string_(miditoname.value(item));

	t = TextField(w, Rect(10+gap*i, 50, gap, 30)).string_(miditoname.value(mapped[i]));
	t.action = { mapped[i] = nametomidi.value(t.value); ("upper map note number " ++ i ++ " from " ++ table[i] ++ " to " ++  mapped[i]).postln;  diffbuf.set(i, (table[i] - mapped[i]).midiratio.reciprocal)};
	tf.add(t);

	u = TextField(w, Rect(10+gap*i, 90, gap, 30)).string_(miditoname.value(mapped2[i]));
	u.action = { mapped2[i] = nametomidi.value(u.value); ("lower map note number " ++ i ++ " from " ++ table[i] ++ " to " ++ mapped2[i]).postln; diffbuf2.set(i, (table[i]-mapped2[i]).midiratio.reciprocal);
	tf2.add(u);
	};
});
// add a button to play a reference note
c = Button(w, Rect(10,130,100,30)).states_([["Play C4",Color.black,Color.gray]]);
c.action_({arg butt;
	if (butt.value == 0,
	{
			var env = Env.perc;
	        SinOsc.ar(nametomidi.value("c4").midicps)*EnvGen.kr(env, doneAction:2)!2;
	}.play,
	{})
});

// also add a start/stop button
// when the button is set to start, instantiate a new Synth, otherwise free the Synth
b= Button(w, Rect(110,130,100,30)).states_([
	["Start",Color.black, Color.red],
	["Stop",Color.black, Color.green]]);
b.action_({arg butt;
	if (butt.value == 1,
		{
			tf.do({arg item; item.action});
			tf2.do({arg item; item.action});
			table.do({arg item, i;
				difference2[i] = (table[i] - mapped2[i]).midiratio.reciprocal;
				difference[i] = (table[i] - mapped[i]).midiratio.reciprocal;
			});
			diffbuf.setn(0,difference);
			diffbuf2.setn(0,difference2);
			sound = Synth.new("pitchFollow1");
		},
		{   sound.free;}
	)
});

// define the Synth itself:
// - first it determines the pitch of what it hears in the microphone
// - then it harmonizes the pitch with the notes as defined in the ui
SynthDef.new("pitchFollow1",{
    var in, amp, freq, hasFreq, out;
	var t, midinum;
	var harmony, harmony2, partials;
    in = Mix.new(SoundIn.ar([0,1]));
	amp = Amplitude.kr(in, 0.05, 1);
    # freq, hasFreq = Pitch.kr(in);
	midinum = freq.cpsmidi.round(1);
	midinum.postln;
    freq = Lag.kr(midinum.midicps, 0.05);
	//freq = midinum.midicps;
	harmony2= WrapIndex.kr(diffbuf2.bufnum, midinum);
	harmony = WrapIndex.kr(diffbuf.bufnum, midinum);
	partials = [
		   0.5,
		   1,
		   0.5*harmony,
		   1*harmony,
		 0.5*harmony2,
		   1*harmony2,
	];
	out = Mix.new(PitchShift.ar(in, 0.2, partials, 0, 0.004));

    7.do({
		out = AllpassN.ar(out, 0.040, [0.040.rand,0.040.rand], 2)
    });

    Out.ar(0,out/partials.size)

}).add;

// make the ui visible
w.front;

)

