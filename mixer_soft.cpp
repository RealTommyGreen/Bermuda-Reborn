/*
 * Bermuda Syndrome engine rewrite
 * Copyright (C) 2007-2011 Gregory Montoir
 */

#include "file.h"
#include "mixer.h"
#include "systemstub.h"
#ifdef BERMUDA_VORBIS
#include <vorbis/vorbisfile.h>
#endif
#ifdef BERMUDA_STB_VORBIS
#include "stb_vorbis.c"
#endif

static const int _fracStepBits = 8;
static const int _sfxVolume = 224;
static const int _musicVolume = 160;

#ifdef BERMUDA_TSF
#define TSF_IMPLEMENTATION
#define TML_IMPLEMENTATION
#include "third_party/tsf.h"
#include "third_party/tml.h"
#endif

struct LockAudioStack {
	LockAudioStack(SystemStub *stub) : _stub(stub) {
		_stub->lockAudio();
	}
	~LockAudioStack() {
		_stub->unlockAudio();
	}
	SystemStub *_stub;
};

static void mixSample(int16_t &dst, int sample, int volume) {
	int pcm = dst + ((sample * volume) >> 8);
	if (pcm < -32768) {
		pcm = -32768;
	} else if (pcm > 32767) {
		pcm = 32767;
	}
	dst = (int16_t)pcm;
}

static int16_t clampSample(int pcm) {
	if (pcm < -32768) {
		return -32768;
	} else if (pcm > 32767) {
		return 32767;
	}
	return (int16_t)pcm;
}

struct MixerChannel {
	virtual ~MixerChannel() {}
	virtual bool load(File *f, int mixerSampleRate) = 0;
	virtual int read(int16_t *dst, int samples) = 0;
	int id;
};

struct MixerChannel_Wav : MixerChannel {
	MixerChannel_Wav()
		: _buf(0), _bufSize(0), _bufReadOffset(0), _bufReadStep(0) {
	}

	virtual ~MixerChannel_Wav() {
		if (_buf) {
			free(_buf);
			_buf = 0;
		}
	}

	virtual bool load(File *f, int mixerSampleRate) {
		char buf[8];
		f->seek(8); // skip RIFF header
		f->read(buf, 8);
		if (memcmp(buf, "WAVEfmt ", 8) == 0) {
			f->readUint32LE(); // fmtLength
			int compression = f->readUint16LE();
			int channels = f->readUint16LE();
			int sampleRate = f->readUint32LE();
			f->readUint32LE(); // averageBytesPerSec
			f->readUint16LE(); // blockAlign
			_bitsPerSample = f->readUint16LE();
			if (compression != 1 ||
			    (channels != 1 && channels != 2) ||
			    (sampleRate != 11025 && sampleRate != 22050 && sampleRate != 44100) ||
			    (_bitsPerSample != 8 && _bitsPerSample != 16)) {
				warning("Unhandled wav/pcm format compression %d channels %d rate %d bits %d", compression, channels, sampleRate, _bitsPerSample);
				return false;
			}
			_stereo = (channels == 2);
			_bufReadStep = (sampleRate << _fracStepBits) / mixerSampleRate;
			f->read(buf, 4);
			if (memcmp(buf, "data", 4) == 0) {
				_bufSize = f->readUint32LE();
				_buf = (uint8_t *)malloc(_bufSize);
				if (_buf) {
					f->read(_buf, _bufSize);
					return true;
				}
			}
		}
		return false;
	}

	bool readSample(int16_t &sample) {
		switch (_bitsPerSample) {
		case 8:
			if ((_bufReadOffset >> _fracStepBits) >= _bufSize) { // end of buffer
				return false;
			}
			sample = (_buf[_bufReadOffset >> _fracStepBits] << 8) ^ 0x8000;
			break;
		case 16:
			if ((_bufReadOffset >> _fracStepBits) * 2 >= _bufSize) { // end of buffer
				return false;
			}
			sample = READ_LE_UINT16(&_buf[(_bufReadOffset >> _fracStepBits) * 2]);
			break;
		}
		_bufReadOffset += _bufReadStep;
		return true;
	}

	virtual int read(int16_t *dst, int samples) {
		for (int i = 0; i < samples; ++i) {
			int16_t sampleL = 0, sampleR;
			if (!readSample(sampleL)) {
				return i;
			}
			sampleR = sampleL;
			if (_stereo && !readSample(sampleR)) {
				return i;
			}
			mixSample(*dst++, sampleL, _sfxVolume);
			mixSample(*dst++, sampleR, _sfxVolume);
		}
		return samples;
	}

	uint8_t *_buf;
	int _bufSize;
	int _bufReadOffset;
	int _bufReadStep;
	int _bitsPerSample;
	bool _stereo;
};

#ifdef BERMUDA_VORBIS
static size_t file_vorbis_read_helper(void *ptr, size_t size, size_t nmemb, void *datasource) {
	if (size != 0 && nmemb != 0) {
		int n = ((File *)datasource)->read(ptr, size * nmemb);
		if (n > 0) {
			return n / size;
		}
	}
	return 0;
}

static int file_vorbis_seek_helper(void *datasource, ogg_int64_t offset, int whence) {
	((File *)datasource)->seek(offset, whence);
	return 0;
}

static int file_vorbis_close_helper(void *datasource) {
	((File *)datasource)->close();
	delete ((File *)datasource);
	return 0;
}

static long file_vorbis_tell_helper(void *datasource) {
	return ((File *)datasource)->tell();
}

struct MixerChannel_Vorbis : MixerChannel {
	MixerChannel_Vorbis()
		: _loop(true), _open(false), _readBuf(0), _readBufSize(0) {
	}

	virtual ~MixerChannel_Vorbis() {
		if (_open) {
			ov_clear(&_ovf);
		}
		free(_readBuf);
	}

	virtual bool load(File *f, int mixerSampleRate) {
		ov_callbacks ovcb;
		ovcb.read_func  = file_vorbis_read_helper;
		ovcb.seek_func  = file_vorbis_seek_helper;
		ovcb.close_func = file_vorbis_close_helper;
		ovcb.tell_func  = file_vorbis_tell_helper;
		if (ov_open_callbacks(f, &_ovf, 0, 0, ovcb) < 0) {
			warning("Invalid .ogg file");
			return false;
		}
		_open = true;
		vorbis_info *vi = ov_info(&_ovf, -1);
		if (vi->channels != 2 || vi->rate != mixerSampleRate) {
			warning("Unhandled ogg/pcm format ch %d rate %d", vi->channels, vi->rate);
			return false;
		}
		return true;
	}

	virtual int read(int16_t *dst, int samples) {
		int dstSize = samples * sizeof(int16_t) * 2;
		if (dstSize > _readBufSize) {
			_readBufSize = dstSize;
			free(_readBuf);
			_readBuf = (char *)malloc(_readBufSize);
			if (!_readBuf) {
				return 0;
			}
		}
		int readSize = 0;
		while (dstSize > 0) {
			int len = ov_read(&_ovf, _readBuf, dstSize, 0, 2, 1, 0);
			if (len < 0) {
				// error in decoder
				return 0;
			}
			if (len == 0) {
				if (_loop) {
					ov_raw_seek(&_ovf, 0);
					continue;
				}
				break;
			}
			// mix pcm data
			for (unsigned int i = 0; i < len / sizeof(int16_t); ++i) {
				const int16_t sample = (int16_t)READ_LE_UINT16(&_readBuf[i * 2]);
				mixSample(dst[readSize + i], sample, _musicVolume);
			}
			readSize += len / sizeof(int16_t);
			dstSize -= len;
		}
		return readSize;
	}

	OggVorbis_File _ovf;
	bool _loop;
	bool _open;
	char *_readBuf;
	int _readBufSize;
};
#endif

#ifdef BERMUDA_STB_VORBIS
struct MixerChannel_StbVorbis : MixerChannel {
	MixerChannel_StbVorbis()
		: _v(0), _f(0) {
	}
	~MixerChannel_StbVorbis() {
		if (_v) {
			stb_vorbis_close(_v);
			_v = 0;
		}
	}
	virtual bool load(File *f, int mixerSampleRate) {
		_count = f->read(_buffer, sizeof(_buffer));
		if (_count > 0) {
			int bytes = 0;
			int error = 0;
			_v = stb_vorbis_open_pushdata(_buffer, _count, &bytes, &error, 0);
			if (_v) {
				_offset = bytes;
				stb_vorbis_info info = stb_vorbis_get_info(_v);
				if (info.channels != 2 || info.sample_rate != mixerSampleRate) {
					warning("Unhandled ogg/pcm format ch %d rate %d", info.channels, info.sample_rate);
					return false;
				}
				_f = f;
				_decodedSamplesLen = 0;
				return true;
			}
		}
		return false;
	}
	virtual int read(int16_t *dst, int samples) {
		int total = 0;
		if (_decodedSamplesLen != 0) {
			const int len = MIN(_decodedSamplesLen, samples);
			for (int i = 0; i < len; ++i) {
				mixSample(*dst++, _decodedSamples[0][i], _musicVolume);
				mixSample(*dst++, _decodedSamples[1][i], _musicVolume);
			}
			total += len;
			_decodedSamplesLen -= len;
		}
		while (total < samples) {
			int channels = 0;
			float **outputs;
			int count;
			int bytes = stb_vorbis_decode_frame_pushdata(_v, _buffer + _offset, _count - _offset, &channels, &outputs, &count);
			if (bytes == 0) {
				if (_offset != _count) {
					memmove(_buffer, _buffer + _offset, _count - _offset);
					_offset = _count - _offset;
				} else {
					_offset = 0;
				}
				_count = sizeof(_buffer) - _offset;
				bytes = _f->read(_buffer + _offset, _count);
				if (bytes < 0) {
					break;
				}
				if (bytes == 0) {
					// rewind
					_f->seek(0, SEEK_SET);
					_count = _f->read(_buffer, sizeof(_buffer));
					stb_vorbis_flush_pushdata(_v);
				} else {
					_count = _offset + bytes;
				}
				_offset = 0;
				continue;
			}
			_offset += bytes;
			if (channels == 2) {
				const int remain = samples - total;
				const int len = MIN(count, remain);
				for (int i = 0; i < len; ++i) {
					mixSample(*dst++, int(outputs[0][i] * 32768 + .5), _musicVolume);
					mixSample(*dst++, int(outputs[1][i] * 32768 + .5), _musicVolume);
				}
				if (count > remain) {
					_decodedSamplesLen = count - remain;
					assert(_decodedSamplesLen < 1024);
					for (int i = 0; i < _decodedSamplesLen; ++i) {
						_decodedSamples[0][i] = int(outputs[0][len + i] * 32768 + .5);
						_decodedSamples[1][i] = int(outputs[1][len + i] * 32768 + .5);
					}
					total = samples;
					break;
				}
			} else {
				warning("Invalid decoded data channels %d count %d", channels, count);
			}
			total += count;
		}
		return total;
	}

	uint8_t _buffer[8192];
	int16_t _decodedSamples[2][1024];
	int _decodedSamplesLen;
	uint32_t _offset, _count;
	stb_vorbis *_v;
	File *_f;
};
#endif

#ifdef BERMUDA_TSF
struct MixerChannel_Midi : MixerChannel {
	MixerChannel_Midi(tsf *tsf)
		: _tsf(tsf), _midiMessages(0), _currentMessage(0),
		  _msPosition(0), _sampleRate(0), _renderBuf(0), _renderBufSize(0) {
	}

	~MixerChannel_Midi() {
		if (_midiMessages) tml_free(_midiMessages);
		free(_renderBuf);
	}

	virtual bool load(File *f, int mixerSampleRate) {
		const int size = f->size();
		void *midiData = malloc(size);
		if (!midiData) return false;
		f->seek(0);
		f->read(midiData, size);

		_midiMessages = tml_load_memory(midiData, size);
		free(midiData);
		if (!_midiMessages) {
			warning("MixerChannel_Midi: failed to parse MIDI");
			return false;
		}

		_currentMessage = _midiMessages;
		_sampleRate = mixerSampleRate;
		_renderBufSize = mixerSampleRate * 4;
		_renderBuf = (int16_t *)malloc(_renderBufSize * sizeof(int16_t));
		if (!_renderBuf) {
			_renderBufSize = 0;
			return false;
		}

		tsf_reset(_tsf);
		tsf_channel_set_bank_preset(_tsf, 9, 128, 0);

		debug(DBG_MIXER, "MixerChannel_Midi loaded rate=%d", mixerSampleRate);
		return true;
	}

	void rewindMidi() {
		_currentMessage = _midiMessages;
		_msPosition = 0;
		tsf_reset(_tsf);
		tsf_channel_set_bank_preset(_tsf, 9, 128, 0);
	}

	virtual int read(int16_t *dst, int samples) {
		if (!_midiMessages || !_tsf) return 0;

		const double msPerSample = 1000.0 / _sampleRate;
		double blockEndMs = _msPosition + samples * msPerSample;

		while (_currentMessage && _currentMessage->time < blockEndMs) {
			switch (_currentMessage->type) {
			case TML_NOTE_ON:
				tsf_channel_note_on(_tsf, _currentMessage->channel,
					_currentMessage->key, _currentMessage->velocity / 127.0f);
				break;
			case TML_NOTE_OFF:
				tsf_channel_note_off(_tsf, _currentMessage->channel,
					_currentMessage->key);
				break;
			case TML_PROGRAM_CHANGE:
				tsf_channel_set_presetnumber(_tsf, _currentMessage->channel,
					_currentMessage->program, 0);
				break;
			case TML_CONTROL_CHANGE:
				if (_currentMessage->control == TML_VOLUME_MSB) {
					tsf_channel_set_volume(_tsf, _currentMessage->channel,
						_currentMessage->control_value / 127.0f);
				} else if (_currentMessage->control == TML_PAN_MSB) {
					tsf_channel_set_pan(_tsf, _currentMessage->channel,
						_currentMessage->control_value / 127.0f);
				}
				break;
			case TML_PITCH_BEND:
				tsf_channel_set_pitchwheel(_tsf, _currentMessage->channel,
					_currentMessage->pitch_bend);
				break;
			}
			_currentMessage = _currentMessage->next;
		}

		if (!_currentMessage) {
			rewindMidi();
			blockEndMs = _msPosition + samples * msPerSample;
			while (_currentMessage && _currentMessage->time < blockEndMs) {
				switch (_currentMessage->type) {
				case TML_NOTE_ON:
					tsf_channel_note_on(_tsf, _currentMessage->channel,
						_currentMessage->key, _currentMessage->velocity / 127.0f);
					break;
				case TML_NOTE_OFF:
					tsf_channel_note_off(_tsf, _currentMessage->channel,
						_currentMessage->key);
					break;
				case TML_PROGRAM_CHANGE:
					tsf_channel_set_presetnumber(_tsf, _currentMessage->channel,
						_currentMessage->program, 0);
					break;
				case TML_CONTROL_CHANGE:
					if (_currentMessage->control == TML_VOLUME_MSB) {
						tsf_channel_set_volume(_tsf, _currentMessage->channel,
							_currentMessage->control_value / 127.0f);
					} else if (_currentMessage->control == TML_PAN_MSB) {
						tsf_channel_set_pan(_tsf, _currentMessage->channel,
							_currentMessage->control_value / 127.0f);
					}
					break;
				case TML_PITCH_BEND:
					tsf_channel_set_pitchwheel(_tsf, _currentMessage->channel,
						_currentMessage->pitch_bend);
					break;
				}
				_currentMessage = _currentMessage->next;
			}
		}

		const int needed = samples * 2;
		if (needed > _renderBufSize) {
			_renderBufSize = needed;
			_renderBuf = (int16_t *)realloc(_renderBuf, _renderBufSize * sizeof(int16_t));
			if (!_renderBuf) return 0;
		}

		tsf_render_short(_tsf, _renderBuf, samples, 0);

		for (int i = 0; i < samples * 2; ++i) {
			mixSample(dst[i], _renderBuf[i], _musicVolume);
		}

		_msPosition = blockEndMs;
		return samples;
	}

	tsf *_tsf;
	tml_message *_midiMessages;
	tml_message *_currentMessage;
	double _msPosition;
	int _sampleRate;
	int16_t *_renderBuf;
	int _renderBufSize;
};
#endif

struct MixerSoftware: Mixer {
	static const int kMaxChannels = 4;

	SystemStub *_stub;
	int _channelIdSeed;
	bool _open;
	MixerChannel *_channels[kMaxChannels];
	int32_t *_mixBuf;
	int16_t *_channelBuf;
	int _mixBufSize;
#ifdef BERMUDA_TSF
	tsf *_tsf;
	char _soundFontPath[512];
#endif

	MixerSoftware(SystemStub *stub)
		: _stub(stub), _channelIdSeed(0), _open(false),
		  _mixBuf(0), _channelBuf(0), _mixBufSize(0) {
		memset(_channels, 0, sizeof(_channels));
#ifdef BERMUDA_TSF
		_tsf = 0;
		_soundFontPath[0] = 0;
#endif
	}

	virtual ~MixerSoftware() {
		for (int i = 0; i < kMaxChannels; ++i) {
			if (_channels[i]) {
				delete _channels[i];
			}
		}
#ifdef BERMUDA_TSF
		if (_tsf) {
			tsf_close(_tsf);
			_tsf = 0;
		}
#endif
		free(_mixBuf);
		free(_channelBuf);
	}

	virtual void setSoundFont(const char *path) {
#ifdef BERMUDA_TSF
		if (path && path[0]) {
			strncpy(_soundFontPath, path, sizeof(_soundFontPath) - 1);
			_soundFontPath[sizeof(_soundFontPath) - 1] = 0;
		} else {
			_soundFontPath[0] = 0;
		}
#endif
	}

#ifdef BERMUDA_TSF
	void loadSoundFontIfNeeded() {
		if (_open && _soundFontPath[0] && !_tsf) {
			File f;
			if (f.open(_soundFontPath)) {
				const int sz = f.size();
				void *data = malloc(sz);
				if (data) {
					f.read(data, sz);
					_tsf = tsf_load_memory(data, sz);
					free(data);
					if (_tsf) {
						tsf_set_output(_tsf, TSF_STEREO_INTERLEAVED, _stub->getOutputSampleRate(), 0);
					}
				}
			}
		}
	}
#endif

	virtual void open() {
		if (!_open) {
			_stub->startAudio(MixerSoftware::mixCallback, this);
			_open = true;
		}
#ifdef BERMUDA_TSF
		loadSoundFontIfNeeded();
#endif
	}

	virtual void close() {
		if (_open) {
			_stub->stopAudio();
			_open = false;
		}
	}

	void startSound(File *f, int *id, MixerChannel *mc) {
		if (mc->load(f, _stub->getOutputSampleRate()) && bindChannel(mc, id)) {
			return;
		}
		*id = kDefaultSoundId;
		delete mc;
	}

	virtual void playSound(File *f, int *id) {
		debug(DBG_MIXER, "Mixer::playSound()");
		LockAudioStack las(_stub);
		startSound(f, id, new MixerChannel_Wav);
	}

	virtual void playMusic(File *f, int *id) {
		debug(DBG_MIXER, "Mixer::playMusic()");
		LockAudioStack las(_stub);
#ifdef BERMUDA_TSF
		loadSoundFontIfNeeded();
#endif
#ifdef BERMUDA_STB_VORBIS
		{
			MixerChannel_StbVorbis *mc = new MixerChannel_StbVorbis;
			if (mc->load(f, _stub->getOutputSampleRate()) && bindChannel(mc, id)) {
				return;
			}
			delete mc;
		}
#endif
#ifdef BERMUDA_VORBIS
		{
			MixerChannel_Vorbis *mc = new MixerChannel_Vorbis;
			if (mc->load(f, _stub->getOutputSampleRate()) && bindChannel(mc, id)) {
				return;
			}
			delete mc;
		}
#endif
#ifdef BERMUDA_TSF
		if (_tsf) {
			f->seek(0);
			MixerChannel_Midi *mc = new MixerChannel_Midi(_tsf);
			if (mc->load(f, _stub->getOutputSampleRate()) && bindChannel(mc, id)) {
				return;
			}
			delete mc;
		}
#endif
		*id = kDefaultSoundId;
	}

	virtual bool isSoundPlaying(int id) {
		debug(DBG_MIXER, "Mixer::isSoundPlaying() 0x%X", id);
		if (id == kDefaultSoundId) {
			return false;
		}
		LockAudioStack las(_stub);
		const int channel = getChannelFromSoundId(id);
		assert(channel >= 0 && channel < kMaxChannels);
		MixerChannel *mc = _channels[channel];
		return (mc && mc->id == id);
	}

	virtual void stopSound(int id) {
		debug(DBG_MIXER, "Mixer::stopSound() 0x%X", id);
		if (id == kDefaultSoundId) {
			return;
		}
		LockAudioStack las(_stub);
		const int channel = getChannelFromSoundId(id);
		assert(channel >= 0 && channel < kMaxChannels);
		MixerChannel *mc = _channels[channel];
		if (mc && mc->id == id) {
			delete mc;
			_channels[channel] = 0;
		}
	}

	virtual void stopAll() {
		debug(DBG_MIXER, "Mixer::stopAll()");
		LockAudioStack las(_stub);
		for (int i = 0; i < kMaxChannels; ++i) {
			if (_channels[i]) {
				delete _channels[i];
				_channels[i] = 0;
			}
		}
	}

	virtual void setMusicMix(void *param, void (*mix)(void *, uint8_t *, int)) {
		_stub->stopAudio();
		if (mix) {
			_stub->startAudio(mix, param);
		} else {
			_stub->startAudio(mixCallback, this);
		}
	}

	void mix(int16_t *buf, int len) {
		assert((len & 1) == 0);
		const int stereoSamples = len;
		if (stereoSamples > _mixBufSize) {
			int32_t *mixBuf = (int32_t *)malloc(stereoSamples * sizeof(int32_t));
			int16_t *channelBuf = (int16_t *)malloc(stereoSamples * sizeof(int16_t));
			if (!mixBuf || !channelBuf) {
				free(mixBuf);
				free(channelBuf);
				memset(buf, 0, len * sizeof(int16_t));
				return;
			}
			free(_mixBuf);
			free(_channelBuf);
			_mixBuf = mixBuf;
			_channelBuf = channelBuf;
			_mixBufSize = stereoSamples;
		}
		memset(_mixBuf, 0, stereoSamples * sizeof(int32_t));
		for (int i = 0; i < kMaxChannels; ++i) {
			MixerChannel *mc = _channels[i];
			if (mc) {
				memset(_channelBuf, 0, stereoSamples * sizeof(int16_t));
				const int framesRead = mc->read(_channelBuf, stereoSamples / 2);
				if (framesRead <= 0) {
					delete mc;
					_channels[i] = 0;
				} else {
					const int samplesRead = framesRead * 2;
					for (int j = 0; j < samplesRead; ++j) {
						_mixBuf[j] += _channelBuf[j];
					}
				}
			}
		}
		for (int i = 0; i < stereoSamples; ++i) {
			buf[i] = clampSample(_mixBuf[i]);
		}
	}

	static void mixCallback(void *param, uint8_t *buf, int len) {
		assert((len & 1) == 0);
		((MixerSoftware *)param)->mix((int16_t *)buf, len / 2);
	}

	int generateSoundId(int channel) {
		++_channelIdSeed;
		_channelIdSeed &= 0xFFFF;
		assert(channel >= 0 && channel < 16);
		return (_channelIdSeed << 4) | channel;
	}

	int getChannelFromSoundId(int id) {
		return id & 15;
	}

	bool bindChannel(MixerChannel *mc, int *id) {
		for (int i = 0; i < kMaxChannels; ++i) {
			if (!_channels[i]) {
				_channels[i] = mc;
				*id = mc->id = generateSoundId(i);
				return true;
			}
		}
		return false;
	}

	void unbindChannel(int channel) {
		assert(channel >= 0 && channel < kMaxChannels);
		if (_channels[channel]) {
			delete _channels[channel];
			_channels[channel] = 0;
		}
	}
};

Mixer *Mixer_Software_create(SystemStub *stub) {
	return new MixerSoftware(stub);
}
