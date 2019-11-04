#!/bin/bash
help() {
    echo "USAGE:"
    echo "\tsudo sh OVX_bigSW.sh -[opt] [value]\n"
    echo "OPTIONS:"
    echo "\t-t [num] (# of Tenant)"
    echo "\t-i [CONTROLLER_MACHINE_IP]"
    exit 0
}
if [ "$#" -ne 4 ]; then
	echo "# of arguments $#"
	echo "arguments should be 4"
	help
fi

while getopts "t:i:h" opt
do
    case $opt in
        t) tenant=$OPTARG
          ;;
        i) ip=$OPTARG
		  ;;        
        h) help ;;
        ?) help ;;
    esac
done


port=10000
switch=9

func1() {
	#create switch
	python ovxctl.py -n createNetwork tcp:192.168.91.129:6633 10.0.0.0 16
	
	#create switch
	python ovxctl.py -n createSwitch 1 00:00:00:00:00:00:00:$(printf "%02x" 1)
	python ovxctl.py -n createSwitch 1 00:00:00:00:00:00:00:$(printf "%02x" 2)
	python ovxctl.py -n createSwitch 1 00:00:00:00:00:00:00:$(printf "%02x" 3)
	python ovxctl.py -n createSwitch 1 00:00:00:00:00:00:00:$(printf "%02x" 4)
	python ovxctl.py -n createSwitch 1 00:00:00:00:00:00:00:$(printf "%02x" 5)

		

	#switch port
	python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:$(printf "%02x" 1) 1
	python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:$(printf "%02x" 1) 3

	python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:$(printf "%02x" 2) 1
	python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:$(printf "%02x" 2) 3

	python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:$(printf "%02x" 3) 1
	python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:$(printf "%02x" 3) 2

	python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:$(printf "%02x" 4) 1
	python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:$(printf "%02x" 4) 2

	python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:$(printf "%02x" 5) 1
	python ovxctl.py -n createPort 1 00:00:00:00:00:00:00:$(printf "%02x" 5) 2


	#link between switch
	python ovxctl.py -n connectLink 1 00:a4:23:05:00:00:00:$(printf "%02x" 1) 2 00:a4:23:05:00:00:00:$(printf "%02x" 3) 1 spf 1
	
	python ovxctl.py -n connectLink 1 00:a4:23:05:00:00:00:$(printf "%02x" 3) 2 00:a4:23:05:00:00:00:$(printf "%02x" 4) 1 spf 1

	python ovxctl.py -n connectLink 1 00:a4:23:05:00:00:00:$(printf "%02x" 4) 2 00:a4:23:05:00:00:00:$(printf "%02x" 5) 1 spf 1

	python ovxctl.py -n connectLink 1 00:a4:23:05:00:00:00:$(printf "%02x" 5) 2 00:a4:23:05:00:00:00:$(printf "%02x" 2) 2 spf 1
	

	#connect vHost to defaultSwitch
	mac=00:00:00:00:$(printf "%02x" 1):01
	python ovxctl.py -n connectHost 1 00:a4:23:05:00:00:00:01 1 $mac
	
	mac=00:00:00:00:$(printf "%02x" 1):02
	python ovxctl.py -n connectHost 1 00:a4:23:05:00:00:00:02 1 $mac


	#start vNet
	python ovxctl.py -n startNetwork 1

	port=$(($port+1))
}


func2() {
	#create switch
	python ovxctl.py -n createNetwork tcp:192.168.91.143:6633 10.0.0.0 16
	
	#create switch
	python ovxctl.py -n createSwitch 2 00:00:00:00:00:00:00:$(printf "%02x" 1)
	python ovxctl.py -n createSwitch 2 00:00:00:00:00:00:00:$(printf "%02x" 2)
	python ovxctl.py -n createSwitch 2 00:00:00:00:00:00:00:$(printf "%02x" 6)
	python ovxctl.py -n createSwitch 2 00:00:00:00:00:00:00:$(printf "%02x" 7)
	python ovxctl.py -n createSwitch 2 00:00:00:00:00:00:00:$(printf "%02x" 8)
	python ovxctl.py -n createSwitch 2 00:00:00:00:00:00:00:$(printf "%02x" 9)

	#switch port
	python ovxctl.py -n createPort 2 00:00:00:00:00:00:00:$(printf "%02x" 1) 2
	python ovxctl.py -n createPort 2 00:00:00:00:00:00:00:$(printf "%02x" 1) 4

	python ovxctl.py -n createPort 2 00:00:00:00:00:00:00:$(printf "%02x" 2) 2
	python ovxctl.py -n createPort 2 00:00:00:00:00:00:00:$(printf "%02x" 2) 4

	python ovxctl.py -n createPort 2 00:00:00:00:00:00:00:$(printf "%02x" 6) 1
	python ovxctl.py -n createPort 2 00:00:00:00:00:00:00:$(printf "%02x" 6) 2

	python ovxctl.py -n createPort 2 00:00:00:00:00:00:00:$(printf "%02x" 7) 1
	python ovxctl.py -n createPort 2 00:00:00:00:00:00:00:$(printf "%02x" 7) 2

	python ovxctl.py -n createPort 2 00:00:00:00:00:00:00:$(printf "%02x" 8) 1
	python ovxctl.py -n createPort 2 00:00:00:00:00:00:00:$(printf "%02x" 8) 2

	python ovxctl.py -n createPort 2 00:00:00:00:00:00:00:$(printf "%02x" 9) 1
	python ovxctl.py -n createPort 2 00:00:00:00:00:00:00:$(printf "%02x" 9) 2


	


	#link between switch
	python ovxctl.py -n connectLink 2 00:a4:23:05:00:00:00:$(printf "%02x" 1) 2 00:a4:23:05:00:00:00:$(printf "%02x" 3) 1 spf 1

	python ovxctl.py -n connectLink 2 00:a4:23:05:00:00:00:$(printf "%02x" 3) 2 00:a4:23:05:00:00:00:$(printf "%02x" 4) 1 spf 1

	python ovxctl.py -n connectLink 2 00:a4:23:05:00:00:00:$(printf "%02x" 4) 2 00:a4:23:05:00:00:00:$(printf "%02x" 5) 1 spf 1
	
	python ovxctl.py -n connectLink 2 00:a4:23:05:00:00:00:$(printf "%02x" 5) 2 00:a4:23:05:00:00:00:$(printf "%02x" 6) 1 spf 1

	python ovxctl.py -n connectLink 2 00:a4:23:05:00:00:00:$(printf "%02x" 6) 2 00:a4:23:05:00:00:00:$(printf "%02x" 2) 2 spf 1


	#connect vHost to defaultSwitch
	mac=00:00:00:00:$(printf "%02x" 2):01
	python ovxctl.py -n connectHost 2 00:a4:23:05:00:00:00:01 1 $mac
	
	mac=00:00:00:00:$(printf "%02x" 2):02
	python ovxctl.py -n connectHost 2 00:a4:23:05:00:00:00:02 1 $mac


	#start vNet
	python ovxctl.py -n startNetwork 2
}

#TENANT 1~15 for creating traffics
vNet=1
echo -n "Press [ENTER] to create vNet1"
read tmp
func1

#TENANT for TEST
echo -n "Press [ENTER] to create vNet2"
read tmp
func2