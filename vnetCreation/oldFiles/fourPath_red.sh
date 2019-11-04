#!/bin/bash
OVXCTL='../utils/ovxctl.py'
python $OVXCTL -n createNetwork tcp:10.0.0.3:10001 10.0.0.0 16
TENENT="2"

python $OVXCTL -n createSwitch $TENENT 00:00:00:00:00:00:00:01
python $OVXCTL -n createSwitch $TENENT 00:00:00:00:00:00:00:02
python $OVXCTL -n createSwitch $TENENT 00:00:00:00:00:00:00:03
python $OVXCTL -n createSwitch $TENENT 00:00:00:00:00:00:00:04
python $OVXCTL -n createSwitch $TENENT 00:00:00:00:00:00:00:05
python $OVXCTL -n createSwitch $TENENT 00:00:00:00:00:00:00:06
python $OVXCTL -n createSwitch $TENENT 00:00:00:00:00:00:00:07
python $OVXCTL -n createSwitch $TENENT 00:00:00:00:00:00:00:08

python $OVXCTL -n createPort $TENENT 00:00:00:00:00:00:00:01 1
python $OVXCTL -n createPort $TENENT 00:00:00:00:00:00:00:01 2
python $OVXCTL -n createPort $TENENT 00:00:00:00:00:00:00:01 3
python $OVXCTL -n createPort $TENENT 00:00:00:00:00:00:00:01 4
python $OVXCTL -n createPort $TENENT 00:00:00:00:00:00:00:01 5
python $OVXCTL -n createPort $TENENT 00:00:00:00:00:00:00:01 6

python $OVXCTL -n createPort $TENENT 00:00:00:00:00:00:00:02 1
python $OVXCTL -n createPort $TENENT 00:00:00:00:00:00:00:02 2

python $OVXCTL -n createPort $TENENT 00:00:00:00:00:00:00:03 1
python $OVXCTL -n createPort $TENENT 00:00:00:00:00:00:00:03 2

python $OVXCTL -n createPort $TENENT 00:00:00:00:00:00:00:04 1
python $OVXCTL -n createPort $TENENT 00:00:00:00:00:00:00:04 2

python $OVXCTL -n createPort $TENENT 00:00:00:00:00:00:00:05 1
python $OVXCTL -n createPort $TENENT 00:00:00:00:00:00:00:05 2

python $OVXCTL -n createPort $TENENT 00:00:00:00:00:00:00:06 1
python $OVXCTL -n createPort $TENENT 00:00:00:00:00:00:00:06 2

python $OVXCTL -n createPort $TENENT 00:00:00:00:00:00:00:07 1
python $OVXCTL -n createPort $TENENT 00:00:00:00:00:00:00:07 2

python $OVXCTL -n createPort $TENENT 00:00:00:00:00:00:00:08 1
python $OVXCTL -n createPort $TENENT 00:00:00:00:00:00:00:08 2
python $OVXCTL -n createPort $TENENT 00:00:00:00:00:00:00:08 3
python $OVXCTL -n createPort $TENENT 00:00:00:00:00:00:00:08 4
python $OVXCTL -n createPort $TENENT 00:00:00:00:00:00:00:08 5
python $OVXCTL -n createPort $TENENT 00:00:00:00:00:00:00:08 6


python $OVXCTL -n connectLink $TENENT 00:a4:23:05:00:00:00:01 3 00:a4:23:05:00:00:00:03 1 spf 1
python $OVXCTL -n connectLink $TENENT 00:a4:23:05:00:00:00:01 5 00:a4:23:05:00:00:00:06 1 spf 1

python $OVXCTL -n connectLink $TENENT 00:a4:23:05:00:00:00:06 2 00:a4:23:05:00:00:00:07 1 spf 1

python $OVXCTL -n connectLink $TENENT 00:a4:23:05:00:00:00:08 3 00:a4:23:05:00:00:00:03 2 spf 1
python $OVXCTL -n connectLink $TENENT 00:a4:23:05:00:00:00:08 5 00:a4:23:05:00:00:00:07 2 spf 1

python $OVXCTL -n connectHost $TENENT 00:a4:23:05:00:00:00:01 6 00:aa:aa:05:00:03
python $OVXCTL -n connectHost $TENENT 00:a4:23:05:00:00:00:08 6 00:aa:aa:05:00:04


python $OVXCTL -n startNetwork $TENENT