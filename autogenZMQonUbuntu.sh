#!/bin/bash
sudo aptitude install -y python-software-properties
sudo add-apt-repository ppa:webupd8team/java
sudo add-apt-repository ppa:chris-lea/zeromq
sudo aptitude update
sudo aptitude install -y oracle-java7-installer oracle-java7-jdk oracle-java7-jre
sudo aptitude install -y libzmq-dev
sudo aptitude install -y daemon


