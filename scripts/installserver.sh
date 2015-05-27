#!/bin/bash

apt-get -y update
apt-get -y install
apt-get -y upgrade

apt-get -y install htop
apt-get -y install vim
apt-get -y install zip
apt-get -y install ntp
apt-get -y install curl
apt-get -y install git

NUM_REGEX='^[0-9]+$'

# INSTALL JAVA 8
CURRENT_JAVA_VERSION=$(java -version 2>&1 | sed 's/.*version "\(.*\)\.\(.*\)\..*"/\1\2/; 1q')
if ! [[ "$CURRENT_JAVA_VERSION" =~ $NUM_REGEX ]] || [ $CURRENT_JAVA_VERSION -lt 18 ]; then
   echo debconf shared/accepted-oracle-license-v1-1 select true | sudo debconf-set-selections
   echo debconf shared/accepted-oracle-license-v1-1 seen true | sudo debconf-set-selections
   add-apt-repository -y ppa:webupd8team/java
   apt-get -y update
   apt-get -y install oracle-java8-installer
   apt-get -y install oracle-java8-set-default

   hasJavaHome=$(grep 'JAVA_HOME' $HOME/.bashrc)
   if [ $? -eq 1 ]
      then
         echo "JAVA_HOME=/usr/lib/jvm/java-8-oracle/jre" >> $HOME/.bashrc
      else
         sed -i -e s/"JAVA_HOME=.*"/"JAVA_HOME=\/usr\/lib\/jvm\/java\-8\-oracle\/jre"/ $HOME/.bashrc
   fi

   . $HOME/.bashrc
fi

#INSTALL ZOOKEEPER
apt-get -y install zookeeper
apt-get -y install zookeeperd
update-rc.d zookeeper defaults

#INSTALL REDIS
MIN_REDIS_VERSION=30
CURRENT_REDIS_VERSION=$(redis-server --version | perl -pe 's/Redis server v=(\d+)\.(\d+).*/$1$2/')
if ! [[ "$CURRENT_REDIS_VERSION" =~ $NUM_REGEX ]] || [ $CURRENT_REDIS_VERSION -lt $MIN_REDIS_VERSION ]; then
   add-apt-repository -y ppa:chris-lea/redis-server
   apt-get -y update
   apt-get -y install redis-server
   echo 1 > /proc/sys/vm/overcommit_memory

   hasOvercommit=$(grep 'vm.overcommit_memory' /etc/sysctl.conf)
   if [ $? -eq 1 ]
      then
         echo "vm.overcommit_memory=1" >> /etc/sysctl.conf
      else
         sed -i -e s/".*vm\.overcommit_memory.*"/"vm.overcommit_memory=1"/ /etc/sysctl.conf
   fi

   hasSoMaxConn=$(grep 'net.core.somaxconn' /etc/sysctl.conf)
   if [ $? -eq 1 ]
      then
         echo "net.core.somaxconn=512" >> /etc/sysctl.conf
      else
         sed -i -e s/".*net\.core\.somaxconn.*"/"net.core.somaxconn=512"/ /etc/sysctl.conf
   fi
fi

# INSTALL GRADLE
GRADLE_VERSION="2.4"
GRADLE_VERSION_NUM=24

CURRENT_GRADLE_VERSION=$(gradle -version | grep 'Gradle' | perl -pe 's/Gradle\s*(\d*)\.(\d*).*/$1$2/')
if ! [[ "$CURRENT_GRADLE_VERSION" =~ $NUM_REGEX ]] || [ $CURRENT_GRADLE_VERSION -lt $GRADLE_VERSION_NUM ]; then
   wget https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip
   unzip -d /tmp/ gradle-$GRADLE_VERSION-bin.zip
   rm gradle-$GRADLE_VERSION-bin.zip
   mv /tmp/gradle-$GRADLE_VERSION /opt/

   hasGradleHome=$(grep 'GRADLE_HOME' $HOME/.bashrc)
   if [ $? -eq 1 ]
      then
         echo "GRADLE_HOME=/opt/gradle-$GRADLE_VERSION" >> $HOME/.bashrc
         echo "export PATH=\$PATH:\$GRADLE_HOME/bin" >> $HOME/.bashrc
      else
         sed -i -e s/"GRADLE_HOME=.*"/"GRADLE_HOME=\/opt\/gradle\-$GRADLE_VERSION"/ $HOME/.bashrc
   fi

   . $HOME/.bashrc
fi

hasS3_MARKETSTEM_KEY=$(grep 'S3_MARKETSTEM_KEY' $HOME/.bashrc)
if [ $? -eq 1 ]
   then
   echo "S3_MARKETSTEM_KEY and S3_MARKETSTEM_SECRET must be configured with access to the marketstem s3 bucket."
   exit 1;
fi

hasS3_MARKETSTEM_SECRET=$(grep 'S3_MARKETSTEM_SECRET' $HOME/.bashrc)
if [ $? -eq 1 ]
   then
   echo "S3_MARKETSTEM_KEY and S3_MARKETSTEM_SECRET must be configured with access to the marketstem s3 bucket."
   exit 1;
fi

# INSTALL MARKETSTEM SERVICE
PROJECT_OWNER="jamespedwards42"
PROJECT_NAME="marketstem"

mkdir -p ~/git && cd ~/git
if [[ ! -d "$PROJECT_NAME" ]] ; then
   echo `git clone http://github.com/$PROJECT_OWNER/$PROJECT_NAME.git`
fi

bash "$PROJECT_NAME/scripts/buildrun.sh"

crontab -l > current_crontab
if [ -z $(crontab -l | grep '$PROJECT_NAME' | grep 'grep' -v) ]
   then
   echo "@reboot sudo -S /bin/bash -i -c \"/root/git/$PROJECT_NAME/scripts/buildrun.sh >> /root/restart_build.log 2>&1\"" >> current_crontab
   echo "@reboot redis-server /etc/redis/sentinel.conf --sentinel" >> current_crontab
   echo "* * * * * sudo -S /bin/bash -i -c \"/root/git/$PROJECT_NAME/scripts/run.sh\"" >> current_crontab

   crontab current_crontab
fi
rm current_crontab

exit 0
