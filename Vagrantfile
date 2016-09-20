# -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant.configure("2") do |config|
  (1..2).each do |i|
    config.vm.define "test-#{i}" do |node|
      node.vm.box = "bento/ubuntu-14.04"
      node.vm.network "private_network", ip: "192.168.212.1#{i}"
    end
  end
end
