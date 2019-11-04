#!/bin/bash
python ovxctl.py -n createNetwork tcp:$1:6633 10.0.0.0 16
python ovxctl.py -n createSwitch 1 00:00:00:00:00:00:01:00
python ovxctl.py -n createSwitch 1 00:00:00:00:00:00:02:00
python ovxctl.py -n createSwitch 1 00:00:00:00:00:00:03:00
python ovxctl.py -n createSwitch 1 00:00:00:00:00:00:04:00
python ovxctl.py -n createSwitch 1 00:00:00:00:00:00:05:00
python ovxctl.py -n createSwitch 1 00:00:00:00:00:00:06:00
python ovxctl.py -n createSwitch 1 00:00:00:00:00:00:07:00
python ovxctl.py -n createSwitch 1 00:00:00:00:00:00:08:00
python ovxctl.py -n createSwitch 1 00:00:00:00:00:00:09:00
python ovxctl.py -n createSwitch 1 00:00:00:00:00:00:0a:00
python ovxctl.py -n createPort 1 00:00:00:00:00:00:01:00 1
python ovxctl.py -n createPort 1 00:00:00:00:00:00:01:00 2
python ovxctl.py -n createPort 1 00:00:00:00:00:00:02:00 1
python ovxctl.py -n createPort 1 00:00:00:00:00:00:02:00 2
python ovxctl.py -n createPort 1 00:00:00:00:00:00:03:00 1
python ovxctl.py -n createPort 1 00:00:00:00:00:00:03:00 2
python ovxctl.py -n createPort 1 00:00:00:00:00:00:04:00 1
python ovxctl.py -n createPort 1 00:00:00:00:00:00:04:00 2
python ovxctl.py -n createPort 1 00:00:00:00:00:00:05:00 1
python ovxctl.py -n createPort 1 00:00:00:00:00:00:05:00 2
python ovxctl.py -n createPort 1 00:00:00:00:00:00:06:00 1
python ovxctl.py -n createPort 1 00:00:00:00:00:00:06:00 2
python ovxctl.py -n createPort 1 00:00:00:00:00:00:07:00 1
python ovxctl.py -n createPort 1 00:00:00:00:00:00:07:00 2
python ovxctl.py -n createPort 1 00:00:00:00:00:00:08:00 1
python ovxctl.py -n createPort 1 00:00:00:00:00:00:08:00 2
python ovxctl.py -n createPort 1 00:00:00:00:00:00:09:00 1
python ovxctl.py -n createPort 1 00:00:00:00:00:00:09:00 2
python ovxctl.py -n createPort 1 00:00:00:00:00:00:0a:00 1
python ovxctl.py -n createPort 1 00:00:00:00:00:00:0a:00 2
python ovxctl.py -n connectLink 1 00:a4:23:05:00:00:00:01 2 00:a4:23:05:00:00:00:02 1 spf 1
python ovxctl.py -n connectLink 1 00:a4:23:05:00:00:00:02 2 00:a4:23:05:00:00:00:03 1 spf 1
python ovxctl.py -n connectLink 1 00:a4:23:05:00:00:00:03 2 00:a4:23:05:00:00:00:04 1 spf 1
python ovxctl.py -n connectLink 1 00:a4:23:05:00:00:00:04 2 00:a4:23:05:00:00:00:05 1 spf 1
python ovxctl.py -n connectLink 1 00:a4:23:05:00:00:00:05 2 00:a4:23:05:00:00:00:06 1 spf 1
python ovxctl.py -n connectLink 1 00:a4:23:05:00:00:00:06 2 00:a4:23:05:00:00:00:07 1 spf 1
python ovxctl.py -n connectLink 1 00:a4:23:05:00:00:00:07 2 00:a4:23:05:00:00:00:08 1 spf 1
python ovxctl.py -n connectLink 1 00:a4:23:05:00:00:00:08 2 00:a4:23:05:00:00:00:09 1 spf 1
python ovxctl.py -n connectLink 1 00:a4:23:05:00:00:00:09 2 00:a4:23:05:00:00:00:0a 1 spf 1
python ovxctl.py -n connectHost 1 00:a4:23:05:00:00:00:01 1 00:00:00:00:00:01
python ovxctl.py -n connectHost 1 00:a4:23:05:00:00:00:0a 2 00:00:00:00:00:02

