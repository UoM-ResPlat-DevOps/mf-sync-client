package vicnode.mf.client.file;

import java.nio.file.Path;

public interface Filter {

    boolean acceptFile(Path file);

    boolean acceptDirectory(Path dir);

}
