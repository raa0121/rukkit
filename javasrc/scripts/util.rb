# encoding: utf-8

require 'java'
import 'org.bukkit.Bukkit'

module Rukkit
  module Util

    extend self

    def broadcast(*messages)
      Bukkit.server.broadcast_message messages.join
    end

    def sec(n)
      (n * 20).to_i
    end

    def later(tick, &block)
      plugin = Bukkit.plugin_manager.get_plugin("rukkit")
      Bukkit.scheduler.schedule_sync_delayed_task(plugin, block, tick)
    end

    def block_below(block)
      add_loc(block.location, 0, -1, 0).block
    end

    def add_loc(loc, x, y, z)
      l = loc.dup
      l.add(x, y, z)
      l
    end

    def plugin_config(key, type = :string)
      def config_as_string(key)
        config_path = "rukkit.plugin_config.#{key}"
        method = "get_#{type}"
        config = Bukkit.plugin_manager.get_plugin('rukkit')
        config.send method, config_path
      end
    end

  end
end
