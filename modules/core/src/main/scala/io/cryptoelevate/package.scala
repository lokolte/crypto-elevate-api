package io

import zio.Has

package object cryptoelevate {
  type Config = Has[Config.Service]
}
