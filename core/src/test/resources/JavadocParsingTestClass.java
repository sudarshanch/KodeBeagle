package x.y.z;

/**
 * Test class comments
 */
public class HelloWorld {

    /**
     * Test class method comments
     */
    public void method() {

        new HelloWorldInterface() {

            public void greet() {

            }

        }

    }

    /**
     * Test class method1 comments
     */
    public void method1() {

        new HelloWorldInterface() {

            public void greet() {

            }

        }

    }

}

/**
 * Test interface comments
 */
interface HelloWorldInterface {
    /**
     * Test interface method comments
     */
    public void greet();

}

/**
 * Test Enum comments
 */
enum Direction {
    NORTH, SOUTH, EAST, WEST;

    /**
     * Test Enum method comments
     */
    public Direction oppose() {
        switch (this) {
            case NORTH:
                return SOUTH;
            case SOUTH:
                return NORTH;
            case EAST:
                return WEST;
            case WEST:
                return EAST;
        }
        return null;
    }
}


/**
 * Test generic class comments
 * */
public class OrderedPair<K, V> {

    private K key;
    private V value;

    /**
     * Test generic class method comments
     * */
    public OrderedPair(K key, V value) {
        this.key = key;
        this.value = value;
    }

    public K getKey()	{ return key; }
    public V getValue() { return value; }
}