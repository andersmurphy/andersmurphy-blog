title: Emacs: streaming radio with emms

One of the few things I miss about commuting by car is the radio. Unfortunately, I don't have a radio at home. But I do have Emacs. Surely Emacs can do radio?

Turns out you can [live stream "radio" over http](https://en.wikipedia.org/wiki/HTTP_Live_Streaming) using [emms](https://www.gnu.org/software/emms/). Here's my current setup for listening to radio 4.

```
(use-package emms
  :config
  (emms-minimalistic)
  (setq emms-player-list '(emms-player-mpv))
  (emms-mode-line-disable)
  (emms-playing-time-disable-display)
  (setq emms-repeat-playlist t)
  (defvar emms-source-file-default-directory)
  (setq emms-source-file-default-directory "~/Dropbox/music/")

  (defun my/radio4 ()
    "Radio 4. These links might break now and then. For latest links see:

https://gist.github.com/bpsib/67089b959e4fa898af69fea59ad74bc3"
    (interactive)
    (emms-play-streamlist
     "http://lstn.lv/bbc.m3u8?station=bbc_radio_fourfm&bitrate=96000")))
```

Behold the wonders of radio, all from the comfort of Emacs.
