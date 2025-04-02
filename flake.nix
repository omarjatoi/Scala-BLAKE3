{
  description = "Pure Scala port of the BLAKE3 reference implementation";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/master";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
        javaVersion = 17;
        jdk = pkgs."jdk${toString javaVersion}";
        sbt = pkgs.sbt.override { jre = jdk; };
      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = [
            jdk
            sbt
            pkgs.metals
            pkgs.scala-next
            pkgs.scalafmt
            pkgs.scalafix
          ];

          shellHook = ''
            echo "Scala-BLAKE3 development environment"
            echo "JDK version: ${toString javaVersion}"
            echo "Using sbt: $(sbt --version | head -n 1)"
          '';
        };
      });
}
