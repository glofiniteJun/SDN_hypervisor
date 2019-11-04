#!/bin/bash
python ovxctl.py -n createNetwork tcp:$1:6633 10.0.0.0 16
python ovxctl.py -n createSwitch 1 00:00:00:00:00:00:00:01
python ovxctl.py -n createSwitch 1 00:00:00:00:00:00:00:02
python ovxctl.py -n createSwitch 1 00:00:00:00:00:00:00:03
python ovxctl.py -n createSwitch 1 00:00:00:00:00:00:00:04
python ovxctl.py -n createSwitch 1 00:00:00:00:00:00:00:05
python ovxctl.py -n createSwitch 1 00:00:00:00:00:00:00:06
python ovxctl.py -n createSwitch 1 00:00:00:00:00:00:00:07
python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:01 1
python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:01 2
python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:02 1
python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:02 2
python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:02 3
python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:03 1
python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:03 2
python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:03 3
python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:04 1
python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:04 2
python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:04 3
python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:05 1
python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:05 2
python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:05 3
python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:06 1
python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:06 2
python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:06 3
python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:07 1
python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:07 2
python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:07 3
python ovxctl.py -n connectLink 1 00:a4:23:05:00:00:00:01 1 00:a4:23:05:00:00:00:02 1 spf 1
python ovxctl.py -n connectLink 1 00:a4:23:05:00:00:00:01 2 00:a4:23:05:00:00:00:03 1 spf 1
python ovxctl.py -n connectLink 1 00:a4:23:05:00:00:00:02 2 00:a4:23:05:00:00:00:04 3 spf 1
python ovxctl.py -n connectLink 1 00:a4:23:05:00:00:00:02 3 00:a4:23:05:00:00:00:05 3 spf 1
python ovxctl.py -n connectLink 1 00:a4:23:05:00:00:00:03 2 00:a4:23:05:00:00:00:06 3 spf 1
python ovxctl.py -n connectLink 1 00:a4:23:05:00:00:00:03 3 00:a4:23:05:00:00:00:07 3 spf 1
python ovxctl.py -n connectHost 1 00:a4:23:05:00:00:00:04 1 00:00:00:00:00:01
python ovxctl.py -n connectHost 1 00:a4:23:05:00:00:00:04 2 00:00:00:00:00:02
python ovxctl.py -n connectHost 1 00:a4:23:05:00:00:00:05 1 00:00:00:00:00:03
python ovxctl.py -n connectHost 1 00:a4:23:05:00:00:00:05 2 00:00:00:00:00:04
python ovxctl.py -n connectHost 1 00:a4:23:05:00:00:00:06 1 00:00:00:00:00:05
python ovxctl.py -n connectHost 1 00:a4:23:05:00:00:00:06 2 00:00:00:00:00:06
python ovxctl.py -n connectHost 1 00:a4:23:05:00:00:00:07 1 00:00:00:00:00:07
python ovxctl.py -n connectHost 1 00:a4:23:05:00:00:00:07 2 00:00:00:00:00:08


