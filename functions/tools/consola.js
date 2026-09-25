/**
 * Lee una contraseña de la consola sin mostrarla.
 *
 * Al pegar, la consola entrega todo junto en un solo bloque ("clave\r\n"): hay que recorrerlo
 * carácter por carácter, o el Enter termina dentro de la contraseña y el login falla.
 */
function pedirOculto(pregunta) {
  return new Promise((resolve) => {
    const stdin = process.stdin;
    process.stdout.write(pregunta);
    stdin.setRawMode(true);
    stdin.resume();
    stdin.setEncoding("utf8");
    let valor = "";
    const terminar = () => {
      stdin.setRawMode(false);
      stdin.pause();
      stdin.removeListener("data", onData);
      process.stdout.write("\n");
      resolve(valor);
    };
    function onData(bloque) {
      for (const ch of bloque) {
        // Un Enter sin nada escrito es el sobrante de haber pegado el comando: se ignora.
        if (ch === "\r" || ch === "\n") { if (valor) return terminar(); continue; }
        if (ch === "\u0004") return terminar();
        if (ch === "\u0003") process.exit(1);
        if (ch === "\u0008" || ch === "\u007f") valor = valor.slice(0, -1);
        else if (ch >= " ") valor += ch;
      }
    }
    stdin.on("data", onData);
  });
}

module.exports = { pedirOculto };
